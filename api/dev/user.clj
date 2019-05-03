(ns user
  (:require [io.pedestal.http :as http]
            [clojure.java.io :as io]
            [fern.easy :as fe]
            [gos.main]
            [com.cognitect.vase.try :refer [try->]]
            [com.cognitect.vase.fern :as vf]
            [com.cognitect.vase.api :as vase.api]
            [datomic.client.api :as d]))

(defonce server (atom nil))

(defn- reader-on-file-or-resource [path]
  (when-let [f (or (io/file path) (io/resource path))]
    (io/reader f)))

(defn devserver [path]
    (try-> path
           reader-on-file-or-resource
           (:! java.io.IOException ioe (fe/print-other-exception ioe path))

           (vf/load path)
           (:! java.io.IOException ioe (fe/print-other-exception ioe path))

           vf/prepare-service
           (:! Throwable t (fe/print-evaluation-exception t))

           (http/default-interceptors)
           (http/dev-interceptors)
           
           vase.api/start-service
           (:! Throwable t (fe/print-other-exception t path))))

(defn reset []
  (when (and @server
          (not= :com.cognitect.vase.try/exit @server))
    (swap! server http/stop))
  (reset! server (devserver "src/config.fern")))

(defonce dclient (atom nil))
(defonce dconn (atom nil))

(defn dconnect []
  (reset! dclient (d/client {:server-type   :ion
                             :region        "us-east-2"
                             :system        "nygard-solo-dev"
                             :creds-profile "gos"
                             :endpoint      "http://entry.nygard-solo-dev.us-east-2.datomic.net:8182/"
                             :proxy-port    8182}))
  (reset! dconn (d/connect @dclient {:db-name "development"})))
