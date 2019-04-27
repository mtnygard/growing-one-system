(ns igles.subs
  (:require [re-frame.core :as rf]
            [igles.db :as db]))

(rf/reg-sub :can-submit?
            (fn [db _]
              (db/submit-enabled? db)))

(rf/reg-sub :counter
            (fn [db _]
              (db/counter db)))

(rf/reg-sub :active-route
            (fn [db _]
              (db/route db)))

(rf/reg-sub :scroll-top
            (fn [db _]
              (db/scroll-top db)))
