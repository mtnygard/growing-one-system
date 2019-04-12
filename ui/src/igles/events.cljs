(ns igles.events
  (:require [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [igles.db :as db]
            [day8.re-frame.tracing :refer-macros [fn-traced]]))

(rf/reg-event-fx :initialize-db
  (fn-traced [] {:db db/initial-value}))

(rf/reg-event-fx :counter-clicked
  (fn-traced [{:keys [db]} _]
    {:db (update db :counter inc)}))

(rf/reg-event-fx :submit-score
  (fn-traced [{:keys [db]} _]
    {:db         (assoc db :submit-enabled false)
     :http-xhrio {:method          :post
                  :uri             "/scores"
                  :params          {:score (:counter db)}
                  :timeout         8000
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success      [:score-submitted]
                  :on-failure      [:submit-score-failed]}}))

(rf/reg-event-fx :score-submitted
  (fn-traced [{:keys [db]} _]
    {:db (assoc db :submit-enabled true)}))

(rf/reg-event-fx :submit-score-failed
  (fn-traced [{:keys [db]} response]
    (println response)
    {:db (assoc db :submit-enabeld true)}))
