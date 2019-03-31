(ns igles.events
  (:require [re-frame.core :as rf]
            [igles.db :as db]
            [day8.re-frame.tracing :refer-macros [fn-traced]]))

(rf/reg-event-fx :initialize-db
  (fn-traced [] {:db db/initial-value}))

(rf/reg-event-fx :counter-clicked
  (fn-traced [{:keys [db]} _]
    {:db (update db :counter inc)}))
