(ns lambdacd-notifications.hipchat.core
  (:require [lambdacd.util :as util]
            [lambdacd-notifications.notifier :refer [Notifier]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clj-http.client :as http-client]
            [clojure.data.json :as json])

  (:import
    (com.google.gson Gson GsonBuilder)

    (ch.viascom.hipchat.api HipChat)
    (ch.viascom.hipchat.api.api RoomsApi)
    (ch.viascom.hipchat.api.models Card Notification)
    (ch.viascom.hipchat.api.models.message MessageColor MessageFormat)
    (ch.viascom.hipchat.api.models.card CardStyle CardFormat CardDescription CardIcon CardActivity CardAttribute CardAttributeValue CardAttributeValueStyle)
    (ch.viascom.groundwork.foxhttp.exception FoxHttpResponseException)
    (java.util UUID ArrayList)
    (java.net URL)))

(def gson (-> (GsonBuilder.) .setPrettyPrinting .create))

(defn hipchat-notification [{text :text card :card}

                            {access_token :access_token
                             base_url     :base_url
                             room_id      :room_id
                             bot_name     :bot_name}]

  (let [^RoomsApi rooms_api         (-> (HipChat. access_token base_url) (.roomsApi))
        notification                (Notification. bot_name nil nil nil true text card)]

    (log/info "hipchat notification" (.toJson gson card))
    (log/info access_token base_url room_id bot_name)

    (try
      (.sendRoomNotification rooms_api room_id notification)
      (catch FoxHttpResponseException e
        (log/warn "failed to send HipChat notification"
                  (-> (.getFoxHttpResponse e) .getStringBody))))))

(defn hipchat-card-attribute
  ([label value]
   (let [value           (doto (CardAttributeValue.)
                               (.setLabel value))]
     (doto (CardAttribute.)
           (.setLabel label)
           (.setValue value)))))

(defn hipchat-card [{url         :url
                     title       :title
                     description :description
                     duration    :duration
                     status      :status}]

  (let [description                      (doto (CardDescription.)
                                               (.setFormat MessageFormat/TEXT)
                                               (.setValue description))

        icon                             (doto (CardIcon.)
                                               (.setUrl "http://www.lambda.cd/img/lambdacd-logo.png"))

        labels                           (doto (ArrayList.)
                                               (.add (hipchat-card-attribute "Status" status))
                                               (.add (hipchat-card-attribute "Duration" duration)))

        card                             (Card.)]

    (.setDescription card description)
    (.setIcon card icon)
    (.setStyle card CardStyle/APPLICATION)
    (.setUrl card url)
    (.setFormat card CardFormat/MEDIUM)
    (.setTitle card title)
    (.setAttributes card labels)
    (.setId card (-> (UUID/randomUUID) (.toString)))
    card))

(defn ->color [status]
  (case status
    :success "good"
    :failure "danger"
    :waiting "warning"
    :running "good"
    :killed  "danger"
    :unknown "danger"))

(defn ->status-message [status]
  (case status
    :success "Success"
    :failure "Failed"
    :waiting "Waiting"
    :running "Running"
    :killed  "Killed"
    :unknown "Unknown"))

(defn ->build-url [ci-host build-number]
  (.toString
    (java.net.URL. (java.net.URL. ci-host) (str "/#/builds/" build-number))))

(defn hipchat-text [{:keys [event]}]
  (get-in event [:final-result :out]))

(defn trim-text [text length]
  (if (> (count text) length)
    (str (subs text 0 length) "...")
    text))

(defn build-payload [ci-host {:keys [build-number status duration event]}]

  (let [description             (get-in event [:final-result :out])
        shortened_description   (trim-text description 495)]

    (hipchat-card
     {:url         (->build-url ci-host build-number)
      :title       (str "Build #" build-number ": " (->status-message status))
      :description shortened_description
      :color       (->color status)
      :status      (->status-message status)
      :duration    (str "Took " duration " seconds to run")})))

(defrecord HipchatNotifier [base-url access-token room-id bot-name ci-host]
  Notifier
  (notify [this overall-build-info]

          (let [hipchat-text   (hipchat-text overall-build-info)
                shortened_text (trim-text hipchat-text 9990)
                hipchat-card   (build-payload ci-host overall-build-info)]

            (hipchat-notification {:text hipchat-text :card hipchat-card}
                                  {:access_token access-token
                                   :base_url     base-url
                                   :room_id      room-id
                                   :bot_name     bot-name}))))
