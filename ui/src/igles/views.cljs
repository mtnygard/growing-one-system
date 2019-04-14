(ns igles.views
  (:require [re-frame.core :as rf]
            [igles.util :refer [<sub]]))

(defn unavailable []
  ;; can't do anything, just put empty content.
  [:div])

(defn capture-panel
  []
  [:div
   [:h1 "This is here."]
   [:p (str "Clicked " @(rf/subscribe [:counter]) " times")]
   [:button {:on-click #(rf/dispatch [:counter-clicked])} "Click This!"]
   [:button {:disabled (not (<sub :can-submit?))
             :on-click #(rf/dispatch [:submit-score])} "Submit your score"]])

(def ^:private view->panel
  {:capture [capture-panel]})

(defn active-route []
  (let [route (<sub :active-route)]
    (view->panel (:view (:data route)) unavailable)))

(defn main-view
  []
  [active-route])
