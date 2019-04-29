(ns igles.events
  (:require [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [igles.db :as db]
            [igles.routes :as routes]
            [day8.re-frame.tracing :refer-macros [fn-traced]]))

(rf/reg-event-fx :initialize-db
                 (fn-traced [] {:db db/initial-value}))

(rf/reg-event-fx :counter-clicked
                 (fn-traced [{:keys [db]} _]
                            {:db (db/increment-counter db)}))

(rf/reg-event-fx :submit-score
                 (fn-traced [{:keys [db]} _]
                            {:db         (db/disable-submit db)
                             :http-xhrio {:method          :post
                                          :uri             "/v1/scores"
                                          :params          {:payload [{"user/score" (db/counter db)}]}
                                          :timeout         8000
                                          :format          (ajax/json-request-format)
                                          :response-format (ajax/json-response-format)
                                          :on-success      [:score-submitted]
                                          :on-failure      [:submit-score-failed]}}))

(rf/reg-event-fx :score-submitted
                 (fn-traced [{:keys [db]} _]
                            {:db (db/enable-submit db)}))

(rf/reg-event-fx :submit-score-failed
                 (fn-traced [{:keys [db]} response]
                            {:db (db/enable-submit db)}))

(rf/reg-event-fx :window-scrolled
                 (fn-traced [{:keys [db]} _]
                            {:db (db/set-scroll-top db (.. js/document -documentElement -scrollTop))}))

(rf/reg-event-fx :create-world
                 (fn-traced [{:keys [db] :as cofx} [_ _ world-name]]
                            ;; This is for UI dev. Will make an HTTP call when hooked to the back end
                            {:db (assoc-in db [db/worlds world-name] {})
                             :dispatch [:navigate (routes/world (db/user db) world-name)]}))