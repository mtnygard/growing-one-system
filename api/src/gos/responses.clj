(ns gos.responses)

(defn response
  ([status body]
   {:status  status
       :headers {}
       :body    body}))

(def ok (partial response 200))
