(ns my-first-pipeline.core
  (:require
    [my-first-pipeline.pipeline :as pipeline]
    [my-first-pipeline.ui-selection :as ui-selection]
    [org.httpkit.server :as http-kit]
    [lambdacd.runners :as runners]
    [lambdacd.util :as util]
    [lambdacd.core :as lambdacd]
    [lambdacd-notifications.core :as notifications]
    [lambdacd-notifications.hipchat.core :as hipchat]
    [clojure.tools.logging :as log])
  (:gen-class))

; hipchat-base-url hipchat-access-token hipchat-room-id ci-host

(def hipchat-notifier
  (hipchat/map->HipchatNotifier
   {:base-url     "<hipchat server>"
    :access-token "<hipchat access token>"
    :room-id      "<hipchat room id>"
    :bot-name     "<bot name>"
    :ci-host      "<lambdacd base url>"}))

(defn -main [& args]
  (let [;; the home dir is where LambdaCD saves all data.
  ;; point this to a particular directory to keep builds around after restarting
         home-dir (util/create-temp-dir)
         config   {:home-dir home-dir
                   :name     "my first pipeline"}
         ;; initialize and wire everything together
         pipeline (lambdacd/assemble-pipeline pipeline/pipeline-def config)
         ;; create a Ring handler for the UI
         app      (ui-selection/ui-routes pipeline)]
    (log/info "LambdaCD Home Directory is " home-dir)
    ;; this starts the pipeline and runs one build after the other.
    ;; there are other runners and you can define your own as well.
    (runners/start-one-run-after-another pipeline)

    (notifications/setup pipeline hipchat-notifier)

    ;; start the webserver to serve the UI
    (http-kit/run-server app
                         {:open-browser? false
                          :port          8080})))
