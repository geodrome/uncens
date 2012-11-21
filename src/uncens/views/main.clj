(ns uncens.views.main
  (:require [uncens.models.page :as page]
            [uncens.models.user :as user]
            [uncens.models.comment :as comment]
            [uncens.models.db :as db]
            [net.cgrand.enlive-html :as h]
            [noir.session :as ses]
            [noir.response :as resp]
            [noir.validation :as vali]
            [noir.cookies :as cookies])
  (:use [uncens.config :only [config]]
        [noir.core :only [defpage defpartial render]]
        [hiccup.core]
        [hiccup.page]
        [hiccup.form]
        [hiccup.element]
        [clj-time.coerce :only [from-sql-date]]
        [clj-time.format :only [formatter unparse]]
        [clj-time.core :only [from-time-zone time-zone-for-offset]])
  (:import [org.apache.commons.validator.routines UrlValidator DomainValidator]))

;;; COMMON

(def ^:const host-name (:host-name config))

(h/deftemplate main-layout "main-layout.html"
  [title content]
  [:title] (h/content (str "Uncensored: " title))
  [:body] (h/append content))

(h/defsnippet info "info.html"
  [:div]
  [])

(h/defsnippet greeting "user-greeting.html"
  [:div#login-info]
  [return-url]
  [:#greeting :h3] (h/content (str "Hello, " (ses/get :username)))
  [:input#return_url] (h/set-attr :value return-url))

(h/defsnippet login-form "login-form.html"
  [:form]
  [return-url]
  [:div#login_form_error] (if-let [e (ses/flash-get :error)]
                            (h/content e))
  [:input#return-url] (h/set-attr :value return-url)
  [:a#register_link] (h/set-attr :href (str "/register?return-url=" return-url)))

(defn error-item
  [[first-error]]
  first-error)

;;; PAGE

; currently unused
(defn format-date
  "wrapper around java classes to format a timestamp date"
  [date pattern]
  (.format (java.text.SimpleDateFormat. pattern)
           (java.util.Date. (Long/parseLong date))))

(defn format-sql-date
  [sql-date pattern]
  ;(unparse (formatter pattern) (from-sql-date sql-date)))
  (unparse (formatter pattern)
           (from-time-zone (from-sql-date sql-date)
                           (time-zone-for-offset 8))))

(h/defsnippet page-snippet "page.html"
  [:div#middle]
  [{:keys [id code title author published_date url content]} host related comments]
  [:h1] (h/content title)
  [:#host :a] (h/do->
                (h/content host)
                (h/set-attr :href (str host-name "site/" host)))
  [:#separator] (if (or author published_date)
                  (h/content " | ")
                  (h/content ""))
  [:#by] (if author
           (h/content " by ")
           (h/content ""))
  [:#author] (h/content author)
  [:#on] (if published_date
           (h/content " on ")
           (h/content ""))
  [:#pub_date] (when published_date
                 (h/content (format-sql-date published_date "MMMM d, yyyy")))
  [:#content] (h/html-content content)
  [:#source_link] (h/do->
                    (h/content url)
                    (h/set-attr :href url))
  [:#sidebar] (h/do->
                (h/prepend (info))
                (if (user/logged-in?)
                  (h/prepend (greeting (str "/page/" code)))
                  (h/prepend (login-form (str "/page/" code))))
                )
  [:#related] (when-not (= [] related) #(identity %))
  [:#related :ul :li] (h/clone-for [i related]
                        [:a]
                        (h/do->
                          (h/content (:title i))
                          ; relative path works because theses are links to other pages
                          (h/set-attr :href (:code i))))
  [:#responses] (h/append (str " (" (count comments) "):"))
  [:.comment] (if (= [] comments)
                (h/content "No responses yet. Be the first to comment.")
                (h/clone-for [i comments]
                  [:.comment-author] (h/html-content (:username i))
                  [:.comment-date] (h/html-content (format-sql-date (:comment_date i)
                                                                    "MMM d, yyyy h:mm a"))
                  [:.comment-body] (h/html-content (:content i))))
  [:#comment-error] (if-let [e (ses/flash-get :comment-error)]
                      (h/content e))
  [:#user-fields] (when-not (user/logged-in?) #(identity %))
    [:#username] (h/set-attr :value (ses/flash-get :form-username))
    [:#username-error] (when-let [e (ses/flash-get :username-error)] (h/content e))
    [:#password-error] (when-let [e (ses/flash-get :password-error)] (h/content e))
    [:#spam-error] (when-let [e (ses/flash-get :spam-error)] (h/content e))
  [:#comment_box] (h/content (ses/flash-get :form-comment))
  [:#comment-form] (h/set-attr :action (str "/page/" code))
  [:div#comment-form-div :form :input#page_id] (h/set-attr :value id))

(defn display-page
  [{:keys [id url title] :as page}]
  (let [comments (comment/get-comments id)
        host (. (java.net.URL. url) getHost)
        rh (page/get-by-host host 10 id)]
    (println "title: " title "\npage:" page "\nrh:" rh "\ncomments:" comments)
    (main-layout title (page-snippet page host rh comments))))

(defpage "/page/:code" {:keys [code]}
  (if-let [page (page/get-by-code code)]
    (do ;(println "inf: " page)
      (display-page page))
    {:status 404 :body "Sorry, this page does not exist."}))

;; Comment

(defpage [:post "/page/:code"] {:keys [code page_id content username email pass pass2 spam remember] :as comm}
  (when username ;; register user first if necessary
    (if (user/add! {:username username :email email :pass pass :pass2 pass2 :spam spam})
      (user/login! {:username username :pass pass} remember)
      (do ;; preserve form inputs
        (ses/flash-put! :form-username username)
        (ses/flash-put! :form-email email)
        (ses/flash-put! :form-comment content))))
  (if (user/logged-in?)
    (comment/add! page_id (ses/get :userid) content)
    (println "Only registered users can post comments."))
  (resp/redirect (str host-name "page/" code "#comment-anchor")))

;;; SITE

(h/defsnippet site-snippet "site.html"
  [:div#middle]
  [host pages]
  [:#host-title] (h/content host)
  [:.entry] (h/clone-for [p pages]
                [:div.title :a] (h/do->
                                  (h/content (:title p))
                                  (h/set-attr :href (str host-name "page/" (:code p))))
                [:span.by] (if (:author p)
                             (h/content " by ")
                             (h/content ""))
                [:span.author] (h/content (:author p))
                [:span.on] (if (:published_date p)
                             (h/content " on ")
                             (h/content ""))
                [:span.pub_date] (when (:published_date p)
                                   (h/content (format-sql-date (:published_date p) "MMMM d, yyyy")))
                [:div.content] (h/html-content (:content p))
                [:div :a.page_link] (h/set-attr :href (str host-name "page/" (:code p))))
  [:#sidebar] (h/do->
                (if (user/logged-in?)
                  (h/append (greeting (str "/site/" host)))
                  (h/append (login-form (str "/site/" host))))
                (h/append (info))))

(defpage "/site/:host" {:keys [host]}
  (if (.isValid (DomainValidator/getInstance) host)
    (let [pages (page/get-by-host host 20 nil)]
      (main-layout host (site-snippet host pages)))
    (str "Sorry, '" host "' is not a valid domain.")))


;;; USER

(defpartial layout [title & content]
  (html5
    [:head
     [:style "label { width:150px; float:left; }
              #user_form_error { color: #C00000; padding: 5px;}"]
     [:title title]]
    [:body
     [:h3 title]
     content]))

(defpartial user-flds [{:keys [username email]}]
  [:div {:id "user_form_error"} (vali/on-error :username error-item)]
  (label "username" "Username: ")
  (text-field "username" username)
  [:div "&nbsp;"] [:div {:id "user_form_error"} (vali/on-error :email error-item)]
  (label "email" "Email: ")
  (text-field "email" email) [:br]
  [:div "&nbsp;"] [:div {:id "user_form_error"} (vali/on-error :pass error-item)]
  (label "pass" "Choose password: ")
  (password-field "pass") [:br]
  [:div "&nbsp;"] (label "pass2" "Confirm password: ")
  (password-field "pass2") [:br][:br])

(defpage "/user/add" {:as usr}
  (layout
    "Add user"
    (form-to [:post "/user/add"]
      (user-flds usr)
      (submit-button "Add user"))))

(defpage [:post "/user/add"] {:as usr}
  (if (user/add! usr)
    (layout
      "Add user"
      [:p "User added!"])
    (render "/user/add" usr)))

(h/defsnippet user-form "user-form.html"
  [:table]
  [{:keys [username email pass pass2 return-url]}]
  [:div#user_form_error] (h/html-content
                           (or (vali/on-error :username error-item)
                               (vali/on-error :email error-item)
                               (vali/on-error :pass error-item)))
  [:input#username] (h/set-attr :value username)
  [:input#email] (h/set-attr :value email)
  [:input#return-url] (h/set-attr :value return-url))


; registration pages currently unused
(defpage registration-page "/register" {:as usr}
  (main-layout "Register" (user-form usr)))
(defpage [:post "/register"] {:as usr}
  (if (user/add! usr)
    (resp/redirect (:return-url usr))
    (render registration-page usr)))

(defpage [:post "/login"] {:as usr}
  (user/login! usr (:remember usr))
  (resp/redirect (:return-url usr)))

(defpage [:post "/logout"] {:as usr}
  (user/logout!)
  (resp/redirect (:return_url usr)))

;;; HOME

;; Retain Page

(defn handle-url
  [url]
  (println (str "handling url: " url))

  ; prevent from uncensoring urls on uncens.com domain
  (if (= (.getHost (java.net.URL. url))
         (.getHost (java.net.URL. host-name)))
    (resp/redirect url)

    ; if the page for this url already exists, serve it right away
    (if-let [code (page/url->code url)]
      (do
        (resp/redirect (str host-name "page/" code)))
      ; else process it first, and then serve it
      (do
        (try ; in case another request adds the url in the mean time
          (page/retain! url)
          (catch com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
                 e (.toString e)))
        (resp/redirect (str host-name "page/" (page/url->code url)))))))

(defpage "/retain" {:keys [url]}
  (if (.isValid (UrlValidator. (into-array ["http" "https"]) UrlValidator/ALLOW_LOCAL_URLS) url)
    (handle-url url)
    (do
      (println "error")
      (ses/flash-put! :url-error (str "The url '" url "' is invalid."))
      (resp/redirect host-name))))

(defpage [:post "/retain"] {:keys [url]}
  (if (.isValid (UrlValidator. (into-array ["http" "https"])) url)
    (handle-url url)
    (do
      (println "error")
      (ses/flash-put! :url-error (str "The url '" url "' is invalid."))
      (resp/redirect host-name))))

(defpage "/" []
  (html5
    (include-css "/css/style.css")
    [:head
     [:title "UnCens"]]
    [:body
     [:div {:id "header-home"}
      [:span {:id "un"} "Un"] [:span {:id "cens"} "Cens"] " - speak your mind."]
     [:div {:class "center"}
      [:div {:class "text"}
       [:p "Many sites delete \"undesirable\" comments. UnCens is a place to comment without censorship..."]]
      (image "/img/censorship.jpg")
      (form-to [:post "/retain"]
        [:div "&nbsp;"]
        (when-let [e (ses/flash-get :url-error)]
          [:div {:class "error"} e])
        [:div {:class "text"} [:strong "Enter URL starting with \"http://\" to uncensor it: "]]
        [:div "&nbsp;"]
        (text-field {:class "text" :size 60} "url")
        [:div "&nbsp;"]
        [:span " "]
        (submit-button {:id "submit-uncensor"} "Uncensor!"))]]))

(defpage [:head "/"] [] "") ; for beanstalk heartbeat

;;; NOT FOUND

(noir.statuses/set-page! 404 "Sorry, there's nothing here.")



