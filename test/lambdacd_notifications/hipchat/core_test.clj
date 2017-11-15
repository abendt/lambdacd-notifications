(ns lambdacd-notifications.hipchat.core-test
  (:require [lambdacd-notifications.hipchat.core :refer :all]
            [clojure.test :refer :all]))

(deftest test-create-hipchat-card-attribute
  (testing "can create hipchat card"
           (is (some? (hipchat-card-attribute "label" "value")))))

