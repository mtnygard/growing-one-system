(ns igles.events
  (:require [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [igles.api :as api]
            [igles.db :as db]
            [igles.routes :as routes]
            [day8.re-frame.tracing :refer-macros [fn-traced]]))

(rf/reg-event-fx :initialize-db
                 (fn-traced [] {:db db/initial-value}))

(rf/reg-event-fx :window-scrolled
                 (fn-traced [{:keys [db]} _]
                            {:db (db/set-scroll-top db (.. js/document -documentElement -scrollTop))}))
