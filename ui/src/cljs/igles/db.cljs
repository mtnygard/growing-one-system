(ns igles.db)

(def default-view :home)

(def initial-value {:user           "ga"
                    :worlds         {}
                    :submit-enabled true
                    :route          {:data {:view default-view}}})

(def submit-enabled? :submit-enabled)
(defn disable-submit [db]
  (assoc db :submit-enabled false))
(defn enable-submit [db]
  (assoc db :submit-enabled true))

(def safe-inc (fnil inc 0))
(def counter :counter)
(defn increment-counter [db] (update db counter safe-inc))

(def route :route)
(defn set-route [db rte] (assoc db route rte))

(def scroll-top :scroll-top)
(defn set-scroll-top [db x] (assoc db scroll-top x))

(def user :user)
(defn set-user [db x] (assoc db user x))

(def worlds :worlds)
(defn set-worlds [db x] (assoc db worlds x))

(defn user-worlds [db user] (get-in db [worlds user]))
