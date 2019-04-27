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

(defn- navclasses [scroll-top]
  (if (>= 200 scroll-top)
    ["navbar" "navbar-toggleable-md" "fixed-top" "sticky-navigation-alt" "navbar-transparent"]
    ["navbar" "navbar-toggleable-md" "fixed-top" "sticky-navigation-alt" "bg-inverse" "navbar-raised"]))

(defn navbar [scroll-top]
  [:nav {:class (navclasses scroll-top)}
   [:button.navbar-toggler.navbar-toggler-right {:data-toggle   "collapse"
                                                 :data-target   "#navbarCollapse"
                                                 :aria-controls "navbarCollapse"
                                                 :aria-expanded "false"
                                                 :aria-label    "Toggle navigation"}
    [:span.ion-grid]]
   [:a.navbar-brand {:href "#"}
    [:img.nav-brand-logo {:src "img/logo-w60.png"}]]
   [:div.collapse.navbar-collapse {:id "navbarCollapse"}
    [:ul.navbar-nav.ml-auto
     [:li.nav-item.dropdown
      [:a.nav-link.dropdown-toggle {:id            "components"
                                    :href          "#"
                                    :data-toggle   "dropdown"
                                    :aria-haspopup "true"
                                    :aria-expanded "false"}
       "Components"]
      [:div.dropdown-menu {:aria-labelledby "components"}
       [:a.dropdown-item.page-scroll {:href "#buttons"} "Buttons"]
       [:a.dropdown-item.page-scroll {:href "#forms"} "Forms"]
       [:a.dropdown-item.page-scroll {:href "#navigation"} "Navigation"]
       [:a.dropdown-item.page-scroll {:href "#progress-pills"} "Progress / Nav Pills"]]]
     [:li.nav-item [:a.nav-link.page-scroll {:href "#icons"} "Icons"]]
     [:li.nav-item [:a.nav-link.page-scroll {:href "#download"} "Download"]]
     [:li.nav-item [:a.nav-link {:target "_blank"
                                 :href   "examples/landing-page.html"} "Example"]]]]])

(defn main-view
  []
  [:<>
   [navbar (<sub :scroll-top)]
   [:div.wrapper
    [:section.colored-section.hero-header
     [:div.container
      [:div.row
       [:div.col-md-8.offset-md-2
        [active-route]]]]]
    [:section.colored-section.bg-inverse
     [:div.container
      [:div.title
       [:h2.heading-semi-black "Basic Components"]]
      [:div {:id "buttons"}
       [:div.title
        [:h3 "Buttons"]
        [:p.lead.text-muted "Multiple Styles"]]
       [:div.row
        [:div.col-md-8.offset-md-2
         [:button.btn.btn-primary "Default"]
         [:button.btn.btn-outline-primary "Outline"]
         [:button.btn.btn-primary.btn-round "Round"]
         [:button.btn.btn-primary.btn-round [:em.ion-android-checkmark-circle] "with icon"]
         [:button.btn.btn-primary.btn-round [:em.ion-power]]
         [:button.btn.btn-link "Link"]]]
       [:div.title.mt-5
        [:p.lead.text-muted "Multiple Sizes"]]
       [:div.row
        [:div.col-md-8.offset-md-2
         [:div.btn.btn-primary.btn-xs "Extra Small"]
         [:div.btn.btn-primary.btn-sm "Small"]
         [:div.btn.btn-primary "Regular"]
         [:div.btn.btn-primary.btn-md "Medium"]
         [:div.btn.btn-primary.btn-lg "Large"]]]]]]]])


