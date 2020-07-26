(ns cambada.cli
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha.reader :as deps.reader]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Console out and operating functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *debug* (System/getenv "DEBUG"))

(defn debug
  "Print if *debug* (from DEBUG environment variable) is truthy."
  [& args]
  (when *debug* (apply println args)))

(def ^:dynamic *info* (not (System/getenv "LEIN_SILENT")))

(defn info
  "Print if *info* (from LEIN_SILENT environment variable) is truthy."
  [& args]
  (when *info* (apply println args)))

(defn warn
  "Print to stderr if *info* is truthy."
  [& args]
  (when *info*
    (binding [*out* *err*]
      (apply println args))))

(defn abort
  "Print msg to standard err and exit with a value of 1."
  [& msg]
  (let [msg-out (concat ["ERROR!"] msg)]
    (binding [*out* *err*]
      (when (seq msg-out)
        (apply println msg-out))
      (System/exit 1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers for cli use
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def base-cli-options
  [["-d" "--deps FILE_PATH" "Location of deps.edn file"
    :default "deps.edn"]

   ["-o" "--out PATH" "Output directory"
    :default "target"]

   ["-h" "--help" "Shows this help"]])

(defn ^:private args->parsed-opts
  [args cli-options]
  (cli/parse-opts args cli-options))

(defn ^:private conj-default-paths [{:keys [paths] :as m}]
  (assoc m :paths
         (-> paths
             set
             (conj "src")
             vec)))

(defn ^:private assoc-default-deps [{:keys [deps] :or {deps {}} :as m}]
  (cond-> m
    (nil? (get deps 'org.clojure/clojure))
    (assoc :deps (assoc deps 'org.clojure/clojure {:mvn/version "1.9.0"}))))

(defn ^:private parsed-opts->task
  [{{:keys [deps main aot] :as options} :options
    :keys [summary errors]}]
  (try
    (let [deps-map (-> deps
                       io/file
                       deps.reader/slurp-deps
                       conj-default-paths
                       assoc-default-deps)
          opts (cond-> options
                 ;; if main is not nil, it needs to be added to aot
                 ;; unless user chose all or main has been added
                 ;; manually to aot
                 (and (not (nil? main))
                      (and (not= (first aot) 'all)
                           (not= (some #(= main %) aot))))
                 (assoc :aot (conj (or aot []) (symbol main))))]
      (-> {:parser {:summary summary
                    :errors errors}}
          (merge opts)
          (assoc :deps-map deps-map)))
    (catch Exception e
      (abort (->> ["Error reading your deps file. Make sure"
                   deps
                   "is existent and correct."]
                  (string/join " "))))))

(defn args->task
  [args cli-options]
  (-> args
      (args->parsed-opts cli-options)
      parsed-opts->task))

(defn usage
  [main description task]
  (->>
   [description
    ""
    (str "Usage: clj -m " main " [options]")
    ""
    "Options:"
    (-> task :parser :summary)]
   (string/join \newline)
   info))

(defn runner
  [{:keys [help? task apply-fn
           entrypoint-main entrypoint-description]}]
  (if help?
    (usage entrypoint-main entrypoint-description task)
    (do (apply-fn task)
        (info "Done!")
        (System/exit 0))))
