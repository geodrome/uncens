(ns uncens.models.page
  (:use [uncens.models.db]
        [korma core]
        [korma.sql fns]
        [feedparser-clj.core])
  (:require [clj-http.client :as client]
            [net.cgrand.enlive-html :as h]
            [clojure.string :as st])
  (:import [de.l3s.boilerpipe.extractors ArticleExtractor DefaultExtractor]
           [org.jsoup Jsoup]
           [org.jsoup.safety Whitelist]))

;; Helpers

(defn encode-id
  [id]
  (when id
    (if (instance? Number id)
      (Long/toString id 36)
      ;; else it's a String
      (Long/toString (Long/parseLong id) 36))))

(defn decode-id
  [code]
  (when code (Long/parseLong code 36)))

(defn to-http
  "converts the protocol to http if it's https"
  [url]
  (if (= 0 (.indexOf url "https"))
    (st/replace-first url "https" "http")
    url))

(defn get-canonical-url
  "hr html-resource from the enlive library"
  [hr base]
  (if-let [canonical (-> (filter #(= (:rel (:attrs %)) "canonical") (h/select hr [:link]))
                         (first)
                         (:attrs)
                         (:href))]
    (if (= (first canonical) \/)  ; test whether relative path used
      (str base canonical)
      canonical)))

(defn get-title
  [hr]
  (first (:content (first (h/select hr [:title])))))

(defn rss-feed?
  [type]
  (and type
       (or (re-find #"rss" type)
           (re-find #"atom" type))))

(defn get-rss-feeds
  "hr is html-resource from the enlive library, base is the base for relative paths,
   returns a lazy seq of urls of all feeds"
  [hr base]
  (map #(if (= (first %) \/) (str base %) %) ; relative path to specify the feed url may be used
    (map #(:href (:attrs %))
      (filter #(rss-feed? (:type (:attrs %))) (h/select hr [:link])))))

(defn entry-with-url
  "feed-url and url are strings"
  [feed-url url]
  (first (drop-while #(and (not= (:link %) url)
                           (not= (:uri %) url)
                           (not= (:url %) url))
                     (:entries (try (parse-feed feed-url)
                                 (catch Exception e nil))))))

(defn get-rss-entry
  [feeds url]
  (first (drop-while nil? (map #(entry-with-url % url) feeds))))

(defn prep-content
  [content type]

  (defn clean-html
    [s]
    (Jsoup/clean s (.addTags (Whitelist/simpleText) (into-array ["br" "p"]))))

  (defn process-newlines
    [text]
    (str "<p>" (st/replace text "\n" "</p><p>") "</p>"))

  (when content
    (let [max 1000
          len (if (< (.length content) max) (.length content) max) ; avoids out of bonds String reference
          temp (subs content 0 len)
          excerpt (str (subs temp 0 (.lastIndexOf temp " ")) " ...") ; ensures we don't cut off mid-word
          ]
      (if (= type "html")
          (clean-html excerpt)
          (clean-html (process-newlines excerpt))))))

(defn add-code
  [pg]
  (when pg
    (assoc pg :code (encode-id (:id pg)))))

;; Gets

(defn get-by-id
  [id]
  (add-code
    (first
      (select page
        (where {:id id})))))

(defn get-by-code
  [code]
  (get-by-id (decode-id code)))

(defn get-by-url
  [url]
  (add-code
    (first
      (select page
        (where {:url url})))))

(defn url->code
  [url]
  (-> (get-by-url url)
      (:id)
      (encode-id)))

(defn get-by-host
  [host lim exclude-id]
  (map add-code (select page
                  (fields :id :title :author :content :published_date)
                  (where {:url [like (str "http://" host "%")] :id [not= exclude-id]})
                  (order :published_date :DESC :uncensored_date :DESC)
                  (limit lim))))

(defmacro get-by-host-macro
  [host fields lim]
  `(map add-code (select page
                   (fields ~@fields)
                   (where {:url [pred-like (str "http://" ~host "%")]})
                   (order :published_date :DESC)
                   (limit ~lim))))

;; Operations

(defn add!
  [url title author content ext pub-date]
  (insert page
    (values {:url url
             :title title
             :author author
             :content (prep-content content type)
             :ext_method ext
             :published_date pub-date})))

(defn ret!
  [url url-obj base h-res]
  ; if you can find rss entry, use that
  (if-let [ent (get-rss-entry (get-rss-feeds h-res base) url)]
    (add! url ; url
          (:title ent) ; title
          (:name (first (:authors ent))) ; author
          (or (:value (first (:contents ent))) ; content
              (:value (:description ent)))
          "rss" ; extraction method
          (:published-date ent)) ; pub_date
    ; no matching entry found in rss feed
    ; use the boilerpipe lib to extract article content
    (let [title (get-title h-res)
          content (.getText (. DefaultExtractor INSTANCE) url-obj)]
      (add! url
            title
            nil ; author
            content
            "DefaultExtractor" ; extraction method
            nil)))) ; pub date

(defn retain!
  [url]
  (let [url-http (to-http url)
        u (java.net.URL. url-http)
        base (str (.getProtocol u) "://" (.getHost u))
        r (h/html-resource u)]
    (if-let [canonical (get-canonical-url r base)]
      (ret! canonical u base r)
      (ret! url-http u base r))))



;; =======================================================================================
;; IDEAS FOR THE FUTURE

; (get-rss-feeds) may also want to filter by rel attribute == 'alternate'
; otherwise you end up picking things like
; <link rel="service.post" type="application/atom+xml" title="Whole Health Source - Atom" href="http://www.blogger.com/feeds/1629175743855013102/posts/default" />

;; URL CLEANING TO ENSURE UNIQUENESS
; convert domain name to lower case since it is not case sensitive
; may want to further clean the url by eliminating everything after # sign

;;; if entry not found it may be because it hasn't been published in the feed yet,
;;; so maybe check again later

; http://www.slashgear.com/google-threatened-acer-with-android-excommunication-claims-alibaba-13247461/
; feed contains the entry, but the :link points to
; http://feeds.slashgear.com/~r/slashgear/~3/Cnh4KO56m5U/
; which in turn redirects to
; http://www.slashgear.com/google-threatened-acer-with-android-excommunication-claims-alibaba-13247461/?utm_source=feedburner&utm_medium=feed&utm_campaign=Feed%3A+slashgear+%28SlashGear%29
;; could request the url of the :link, and then check canonical on that page
;;; could process page quickly, and then continue the processing in the background and
;;; update the page when ready
; :uri points to http://www.slashgear.com/?p=247461 which redirects to the correct url
; so I would need to follow the redirects, but this could mean pinging :link :uri :url of


; MalformedURLException unknown protocol: tag  java.net.URL.<init> (URL.java:574)
; this happens when uri = "tag:blogger.com,1999:blog-1629175743855013102.post-2193489903959776218"
; for example
;(defn get-redir
;  [url]
;  (let [response (client/get url {:follow-redirects false})]
;    (if (= (:status response) 301)
;      (get-in response [:headers "location"]))))

;
; e = (:entries (parse-feed url))
; (time (doall (map #(get-redir (:uri %)) e)))
; (first (drop-while #(not= (get-redir (:uri %)) u) e)
; (pmap #(if (= (get-redir (:uri %)) u) % nil) e)

; canonical url may be specified but in fact not exist, then use original url
; what if the canonical is on a different domain? this is rare and maybe even it makes sense
;; in that case to use the canonical anyway

; if there is a feed with a matching post, use THAT url, don't check canonical first
;; or check for both canonical and original
;; extract feeds from original, canonical or both?

; strip all query params from url and see if you still get the same content

; take away the www or always keep www (or do whatever the server does?)
;; but if they change the server's behavior?

;; (ret!) sometimes get this exception, need to handle:
; IOException Server returned HTTP response code: 503 for URL:
; http://lifestylejourney.blogspot.com/feeds/posts/default  sun.net.www.protocol.http.HttpURLConnection.getInputStream (HttpURLConnection.java:1436)