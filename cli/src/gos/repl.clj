(ns gos.repl
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.datafy :refer [datafy nav]]
            [gos.db :as db]
            [gos.seq :as seq]
            [gos.spec-print :as sprint]
            [gos.world :as world]
            [datomic.api :as d]
            [clojure.spec-alpha2 :as s])
  (:import clojure.lang.LineNumberingPushbackReader
           java.io.StringReader))

;; ----------------------------------------
;; Error reporting

(defn err->msg
  "Helper to return an error message string from an exception."
  [^Throwable e]
  e)

;; ----------------------------------------
;; Command line definition

(def cli-options
  ;; An option with a required argument
  [["-m" "--in-memory" "Keep data in memory." :default true :id :in-memory]
   #_["-d" "--on-disk DIR" "Keep data in a directory on local disk." :id :on-disk]
   ["-e" "--eval SCRIPT" "Load SCRIPT before taking interactive commands. May be filename or URL"
    :default []
    :assoc-fn (fn [m k v] (update m k seq/conjv v))]
   ["-h" "--help"]])

;; ----------------------------------------
;; New Read-Eval-Print-Loop

;; This is closely modeled on Clojure's own REPL.
;; See clojure.main

(defn skip-if-eol
  "If the next character on stream s is a newline, skips it, otherwise
  leaves the stream untouched. Returns :line-start, :stream-end, or :body
  to indicate the relative location of the next character on s. The stream
  must either be an instance of LineNumberingPushbackReader or duplicate
  its behavior of both supporting .unread and collapsing all of CR, LF, and
  CRLF to a single \\newline."
  [s]
  (let [c (.read s)]
    (cond
      (= c (int \newline)) :line-start
      (= c -1)             :stream-end
      :else                (do (.unread s c) :body))))


(def ^:private phase-names {:evaluate "evaluation" :read "reading" :print "printing"})

(defn- error-phase [msg ph e] (ex-info msg {:error/phase ph} e))
(def read-error (partial error-phase "Error reading" :read-source))
(def eval-error (partial error-phase "Error during evaluation" :evaluate))
(def print-error (partial error-phase "Error printing" :print-result))

(defn- repl-prompt
  []
  (printf " => ")
  (flush))

(defn read-statement
  ([s]
   (read-statement s ""))
  ([s buf]
   (let [c (.read s)]
     (if (= -1 c)
       :quit
       (let [buf (str buf (char c))]
         (if (= (int \;) c)
           buf
           (recur s buf)))))))

(defn repl-read
  "Return the next non-comment, non-whitespace statement. May span multiple lines."
  []
  (let [input (read-statement *in*)]
    (when-not (= :quit input)
      (skip-if-eol *in*))
    input))

(defn- repl-eval
  [state input]
  (try
    (world/process (world/with-input state input))
    (catch Throwable t t)))

(def repl-print (comp sprint/print datafy))

(defn repl-caught
  [e]
  (binding [*out* *err*]
    (pprint (err->msg e))
    (flush)))

;; during init, we break on any error and do not continue reading
(defn- repl-run-init
  [stream state]
  (let [print     repl-print
        caught    repl-caught
        eval      (fn [state input]
                    (try
                      (repl-eval state input)
                      (catch Exception e (throw (eval-error e)))))
        read-eval (fn [state]
                    (try
                      (let [input (try
                                    (repl-read)
                                    (catch Exception e (throw (read-error e))))]
                        (or (#{:quit} input)
                          (eval state input)))
                      (catch Throwable e
                        (caught e)
                        :quit)))]
    (binding [*in* stream]
      (loop [state state]
        (if-not
            (try
              (= :quit (read-eval state))
              (catch Throwable e
                (caught e)
                nil))
          (recur state)
          state)))))

(defn- repl-run
  [init state]
  (let [prompt          repl-prompt
        need-prompt     (if (instance? LineNumberingPushbackReader *in*)
                          #(.atLineStart ^LineNumberingPushbackReader *in*)
                          #(identity true))
        init            (or init (fn [_]))
        flush           flush
        read            repl-read
        eval            (fn [state input]
                          (try
                            (repl-eval state input)
                            (catch Exception e (throw (eval-error e)))))
        print           repl-print
        caught          repl-caught
        read-eval-print (fn [state]
                          (try
                            (let [input (try
                                          (read)
                                          (catch Exception e (throw (read-error e))))]
                              (or
                                (= :quit input)
                                (let [value (eval state input)]
                                  (try
                                    (print value)
                                    value
                                    (catch Throwable e
                                      (throw (print-error e)))))))
                            (catch Throwable e
                              (caught e))))]
    (try
      (init state)
      (catch Throwable e
        (caught e)))
    (prompt)
    (flush)
    (loop [state state]
      (when-not
          (try
            (= :quit (read-eval-print state))
            (catch Throwable e
              (caught e)
              nil))
          (when (need-prompt)
            (prompt)
            (flush))
          (recur state)))))

(s/def ::error (s/keys :req-un [::trace ::cause ::via]))

(defn- join-lines [s] (str/replace s #"\n" ""))

(sprint/use ::error
  (fn [ex]
    (print (:cause ex))
    (print "\nException stack")
    (pp/print-table (map #(-> %
                            (update :message join-lines)
                            (dissoc :at))
                      (:via ex)))))

(s/def ::tx-data (s/coll-of #(instance? datomic.db.Datum %)))
(s/def ::ok-response (s/schema [::tx-result ::query-result]))
(s/def ::tx-response (s/select ::ok-response [:tx-result {:tx-result (s/coll-of (s/select ::tx-data [::tx-data]))}]))
(s/def ::q-response (s/select ::ok-response [:query-result {:query-result set?}]))

(defn- eavta?     [d] (mapv #(nth d %) [0 1 2 3 4]))
(defn- datom->map [d] (zipmap [:e :a :v :tx :added?] (eavta? d)))
(defn- attribute-name [e db-adapter] (:db/ident (db/e db-adapter e)))

(sprint/use ::tx-response
  (fn [result]
    (let [datom-maps (-> result :response :body :tx-result (->> (mapcat :tx-data) (map datom->map)))
          datom-maps (map #(update % :a attribute-name (-> result :dbadapter)) datom-maps)]
      (if-not (empty? datom-maps)
        (pp/print-table datom-maps)
        (pp/pprint result)))))

(sprint/use ::q-response
  (fn [result]
    (let [matched (-> result :query-result)
          fields  (mapv name (-> result :query-fields))
          fields  (if (empty? fields)
                    (let [field-count (reduce max 0 (map count matched))]
                      (map str (range field-count))))]
      (pp/print-table (map #(zipmap fields %) matched)))))

(defn usage [options-summary]
  (->>
    ["Interact with your data."
     ""
     "Usage: java -jar gos.api-VERSION-standalone.jar [options]"
     ""
     "Options:"
     options-summary]
    (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def datomic-memory-uri "datomic:mem://repl")

(def ^:private mutually-exclusive "Only one of --on-disk or --in-memory can be used.")

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)                               {:exit-message (usage summary) :ok? true}
      errors                                        {:exit-message (error-msg errors)}
      (and (:on-disk options) (:in-memory options)) {:exit-message mutually-exclusive}
      :else                                         {:options options :arguments arguments})))

(defn- choose-data-location [{:keys [on-disk in-memory]}]
  (cond
    in-memory "datomic:mem://repl"
    on-disk   "datomic:dev://localhost:4334"))

(defn- nested-continuations
  [f xs]
  (reduce (fn [inner x] (fn [state] (f x (inner state)))) identity xs))

(defn- repl-init [{:keys [eval] :as options}]
  (println "eval: " eval)

  ;; deferred continuation style
  ;; build a chain of evaluations of
  ;; (repl-run-init reader (repl-run-init reader ,,,))
  ;; Return a partial that awaits the initial state
  (let [sources (map #(LineNumberingPushbackReader. (io/reader %)) eval)]
    (nested-continuations repl-run-init sources)))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)
        datomic-uri                        (choose-data-location options)]
    (if exit-message
      (exit ok? exit-message)
      (let [state (world/initial-state (db/classic datomic-uri))]
        (repl-run (repl-init options) state)
        (shutdown-agents)))))


(comment

  ;; to use gos REPL from Clojure REPL
  (def dbadapter (db/classic datomic-memory-uri))
  (defn p [s]
    (repl-print
      (repl-eval
        (world/initial-state dbadapter)
        s)))


  )
