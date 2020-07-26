(ns gos.repl
  (:gen-class)
  (:refer-clojure :exclude [print])
  (:require [clojure.datafy :refer [datafy]]
            [clojure.java.io :as io]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.alpha.spec :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [gos.db :as db]
            [gos.exec :as exec]
            [gos.seq :as seq]
            [gos.spec-print :as sprint]
            [gos.table :refer [print-table]]
            gos.mustache
            [gos.parser :as parser])
  (:import clojure.lang.LineNumberingPushbackReader))

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
   ["-q" "--quit"        "Exit after running the script(s). Only makes sense with -e"]
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
       ":quit"
       (let [buf (str buf (char c))]
         (if (parser/command-complete? buf)
           buf
           (recur s buf)))))))

(defn repl-read
  "Return the next non-comment, non-whitespace statement. May span multiple lines."
  []
  (try
    (let [input (read-statement *in*)]
      (when-not (= ":quit" input)
        (skip-if-eol *in*))
      input)
    (catch Exception e (throw (read-error e)))))

(defn- repl-eval
  [state input]
  (try
    (exec/process (exec/with-input state input))
    (catch Throwable t (throw (eval-error t)))))

(defn- repl-print
  [state]
  (try
    (when (:print state)
      (sprint/print (datafy state)))
    (catch Exception e (throw (print-error e)))))

(defn- repl-caught
  [e]
  (binding [*out* *err*]
    (pprint (err->msg e))
    (flush)))

;; beware side effects
(defn need-prompt []
  (if (instance? LineNumberingPushbackReader *in*)
    (.atLineStart ^LineNumberingPushbackReader *in*)
    false))

(defn- quit! [state] (assoc state :quit! true))
(defn- continue? [state] (not (contains? state :quit!)))
(defn- continue [state] (dissoc state :quit!))
(defn- print [state b] (assoc state :print b))

(defn- repl-control-command? [input]
  (str/starts-with? input ":"))

(defn- extract-control-command [input]
  (-> input
      (str/replace #"[;:\n]" "")
      str/trim))

(defmulti apply-control-command (fn [_ input] (keyword (extract-control-command input))))
(defmethod apply-control-command :quit
  [state _]
  (quit! state))

(defmethod apply-control-command :print
  [state _]
  (print state true))

(defmethod apply-control-command :noprint
  [state _]
  (print state false))

;; during init, we break on any error and do not continue reading
(defn- repl-run-init
  [stream state]
  (let [state           (print (continue state) false)
        read-eval-print (fn [state]
                          (try
                            (let [input (repl-read)]
                              (if (repl-control-command? input)
                                (apply-control-command state input)
                                (let [next-state (repl-eval state input)]
                                  (repl-print next-state)
                                  next-state)))
                            (catch Throwable e
                              (repl-caught e)
                              (quit! state))))]
    (binding [*in* stream]
      (loop [state state]
        (let [next-state (read-eval-print state)]
          (if-not (continue? next-state)
            next-state
            (recur next-state)))))))

(defn- repl-run
  [init state]
  (let [read-eval-print (fn [state]
                          (try
                            (let [input (repl-read)]
                              (if (repl-control-command? input)
                                (apply-control-command state input)
                                (let [next-state (repl-eval state input)]
                                  (repl-print next-state)
                                  next-state)))
                            (catch Throwable e
                              (repl-caught e)
                              state)))
        state           (try (init state)
                             (catch Throwable e
                               (repl-caught e)
                               state))]
    (when-not (:quit-after-init? state)
      (repl-prompt)
      (loop [state (print (continue state) true)]
        (let [next-state (read-eval-print state)]
          (when (continue? next-state)
            (when (need-prompt)
              (repl-prompt))
            (recur next-state)))))))

(s/def ::error (s/keys :req-un [::trace ::cause ::via]))

(defn- join-lines [s] (str/replace s #"\n" ""))

(sprint/use ::error
            (fn [ex]
              (println "An error was thrown:\n")
              (println (:cause ex))
              (println "\nException stack")
              (print-table (map #(-> %
                                     (update :message join-lines)
                                     (dissoc :at))
                                (:via ex)))))

(s/def ::tx-data (s/coll-of #(instance? datomic.db.Datum %)))

(s/def ::response (s/schema [::value]))
(s/def ::ok-value (s/select ::response [:value]))

(s/def ::tx-result (s/schema [::db-before ::db-after ::tx-data ::tempids]))
(s/def ::tx-value (s/select ::tx-result [:tx-data]))

(s/def ::query-result (s/schema [::query-result ::query-fields]))
(s/def ::query-value (s/select ::query-result [:query-result :query-fields]))

(s/def ::problems (s/schema [::problems]))
(s/def ::problems-response (s/select ::problems [:problems]))

(s/def ::multivalue sequential?)

(def ^:private ^:dynamic *dbadapter* nil)

(sprint/use ::ok-value
            (fn [result]
              (binding [*dbadapter* (:dbadapter result)]
                (sprint/print (:value result)))))

(sprint/use ::multivalue
            (fn [vs]
              (doseq [v vs]
                (sprint/print (datafy v)))))

(sprint/use ::problems-response
            (fn [result]
              (doseq [p (:problems result)]
                (sprint/print (datafy p)))))

(defn- eavta?     [d] (mapv #(nth d %) [0 1 2 3 4]))
(defn- datom->map [d] (zipmap [:e :a :v :tx :added?] (eavta? d)))
(defn- attribute-name [e db-adapter] (:db/ident (db/e db-adapter e)))

(sprint/use ::tx-value
            (fn [result]
              (let [datom-maps (map datom->map (:tx-data result))
                    datom-maps (map #(update % :a attribute-name *dbadapter*) datom-maps)]
                (if-not (empty? datom-maps)
                  (print-table datom-maps)
                  (pp/pprint result)))))

(sprint/use ::query-value
            (fn [result]
              (let [matched (:query-result result)
                    fields  (mapv name (:query-fields result))
                    fields  (if (empty? fields)
                              (let [field-count (reduce max 0 (map count matched))]
                                (map str (range field-count)))
                              fields)]
                (print-table (map #(zipmap fields %) matched)))))

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

(defn- repl-init [{:keys [eval]}]
  ;; deferred continuation style
  ;; build a chain of evaluations of
  ;; (repl-run-init reader (repl-run-init reader ,,,))
  ;; Return a partial that awaits the initial state
  (let [sources (map #(LineNumberingPushbackReader. (io/reader %)) eval)]
    (nested-continuations repl-run-init sources)))

(defn- initial-state [options]
  (let [datomic-uri (choose-data-location options)
        base-state  (exec/initial-state (db/classic datomic-uri))]
    (cond-> base-state
      (:quit options)
      (assoc :quit-after-init? true))))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit ok? exit-message)
      (let [state (initial-state options)]
        (repl-run (repl-init options) state)
        (shutdown-agents)))))


(comment

  ;; to use gos REPL from Clojure REPL
  (def datomic-memory-uri "datomic:mem://repl")

  (def dbadapter (db/classic datomic-memory-uri))
  (defn p [s]
    (repl-print
     (repl-eval
      (exec/initial-state dbadapter)
      s))))
