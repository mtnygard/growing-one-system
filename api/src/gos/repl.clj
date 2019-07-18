(ns gos.repl
  (:refer-clojure :exclude [eval print read])
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [gos.db :as db]
            [gos.world :as world])
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
   ["-e" "--eval SCRIPT" "Load SCRIPT before taking interactive commands. May be filename or URL"]
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


(defn- error-phase [ph e] (ex-info nil {:error/phase ph} e))
(def read-error (partial error-phase :read-source))
(def eval-error (partial error-phase :evaluate))
(def print-error (partial error-phase :print-result))

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
  [db x]
  (try
    (world/process (world/current-state db {}) x)
    (catch Throwable t t)))

(def repl-print clojure.pprint/pprint)

(defn repl-caught
  [e]
  (binding [*out* *err*]
    (pprint (err->msg e))
    (flush)))

(defn- repl-run
  [init datomic-uri]
  (let [db              (db/classic datomic-uri)
        prompt          repl-prompt
        need-prompt     (if (instance? LineNumberingPushbackReader *in*)
                          #(.atLineStart ^LineNumberingPushbackReader *in*)
                          #(identity true))
        init            (or init #())
        flush           flush
        read            repl-read
        eval            (fn [input]
                          (try
                            (repl-eval db input)
                            (catch Exception e (throw (eval-error e)))))
        print           repl-print
        caught          repl-caught
        read-eval-print (fn []
                          (try
                            (let [input (try
                                          (read)
                                          (catch Exception e (throw (read-error e))))]
                              (or (#{:quit} input)
                                (let [value (eval input)]
                                  (try
                                    (print value)
                                    (catch Throwable e
                                      (throw (print-error e)))))))
                            (catch Throwable e
                              (caught e))))]
    (try
      (init)
      (catch Throwable e
        (caught e)))
    (prompt)
    (flush)
    (loop []
      (when-not (try
            (= :quit (read-eval-print))
            (catch Throwable e
              (caught e)
              nil))
          (when (need-prompt)
            (prompt)
            (flush))
          (recur)))))

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
;;  (System/exit status)
  )

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

(defn- repl-init [{:keys [eval] :as options}]
  (println "options: " options)
  (println "eval: " eval)
  nil
  )

(defn- inputs [{:keys [eval] :as options}]
  (println "options: " options)
  (println "eval: " eval)
  (if eval
    [[(io/reader eval) false] [*in* true]]
    [[*in* true]]))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)
        datomic-uri                        (choose-data-location options)]
    (if exit-message
      (exit ok? exit-message)
      (do
        (repl-run (repl-init options) datomic-uri)
        (shutdown-agents)))))
