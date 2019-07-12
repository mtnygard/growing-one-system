(ns gos.repl
  (:refer-clojure :exclude [eval print read])
  (:gen-class)
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [gos.db :as db]
            [gos.world :as world]
            [clojure.java.io :as io]))

(def cli-options
  ;; An option with a required argument
  [["-m" "--in-memory" "Keep data in memory." :default true :id :in-memory]
   #_["-d" "--on-disk DIR" "Keep data in a directory on local disk." :id :on-disk]
   ["-e" "--eval SCRIPT" "Load SCRIPT before taking interactive commands. May be filename or URL"]
   ["-h" "--help"]])

(defn- prompt
  []
  (printf " => ")
  (flush))

(defn- read
  [source interactive?]
  (when interactive?
    (prompt))
  (binding [*in* source]
    (let [line (read-line)]
      (cond
        (nil? line)      ::eof
        (= ":quit" line) ::quit
        :else            line))))

(defn- print
  [x]
  (cond
    (and (map? x) (contains? x :response)) (pp/pprint (:response x))
    (and (map? x) (contains? x :problems)) (pp/pprint (:problems x))
    :else                                  (pp/pprint x)))

(defn- eval
  [db x]
  (try
    (world/process (world/current-state db {}) x)
    (catch Throwable t t)))

(defn has-command? [buf]
  (str/index-of buf ";"))

(defn first-command [buf]
  (let [split-point (inc (has-command? buf))]
    [(subs buf 0 split-point) (subs buf split-point)]))

(defn warn [& msg]
  (println "Warning: " (apply str msg)))

(defn run
  [sources datomic-uri]
  (loop [sources sources
         db      (db/classic datomic-uri)
         accum   ""]
    (when-let [[source interactive?] (first sources)]
      (if (has-command? accum)
        (let [[command remainder] (first-command accum)]
          (when-not (= ::quit command)
            (let [v (eval db command)]
              (print v)
              (recur sources db remainder))))
        (let [l (read source interactive?)]
          (case l
            ::eof (do
                    (when (str/blank? accum)
                      ;; TODO - include file and line info here
                      (warn "Ignoring unfinished command " accum))
                    (recur (rest sources) db ""))
            ::quit nil
            (recur sources db (str accum " " l))))))))

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
        (run (inputs options) datomic-uri)
        (shutdown-agents)))))
