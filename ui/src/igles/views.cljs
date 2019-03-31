(ns igles.views
 (:require [re-frame.core :as rf]))

(defn capture-app
  []
  [:div
   [:h1 "This is here."]
   [:p (str "Clicked " @(rf/subscribe [:counter]) " times")]
   [:button {:on-click #(rf/dispatch [:counter-clicked])} "Click This!"]])
