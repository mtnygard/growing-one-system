(ns igles.events
  (:require [re-frame.core :as rf]
            [igles.db :as db]))

(rf/reg-event-fx :initialize-db
  (fn [] db/initial-value))
