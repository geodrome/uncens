(ns uncens.models.comment
  (:use [uncens.models.db]
        [korma core])
  (:require [clojure.string :as st]
            [noir.session :as session])
  (:import [org.jsoup Jsoup]
           [org.jsoup.safety Whitelist]))

(def max-comment-chars 40000)

;; Helpers
(defn clean-html
  [s]
 (Jsoup/clean s (.addTags (Whitelist/simpleText) (into-array ["br" "p"]))))

;; two or more newlines = new paragraph
; one newline = break
(defn process-newlines
  [comment]
  (str "<p>"
       (-> (st/replace comment "\r" "")
           (st/replace #"\n{2,}" "</p><p>")
           (st/replace #"\n" "<br />"))
       "</p>"))

;; Gets

(defn get-comments
  [page-id]
  (select comm
    (with user (fields :username))
    (where {:page_id page-id})))

;; Operations

(defn add!
  [page-id user-id content]
  (if (< (.length content) max-comment-chars)
    (insert comm
      (values {:page_id page-id
               :user_id user-id
               :content (clean-html
                          (process-newlines content))}))
    ; else
    (do
      (session/flash-put! :comment-error
                          (str "Comment length exceeds the maxium of "
                                max-comment-chars
                                " characters"))
      (session/flash-put! :form-comment content))))
