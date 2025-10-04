(ns reitit-middleware-test
  (:require [cljs.test :as test :refer [deftest is async use-fixtures]]
            [reitit.core :as r]
            [redlobster.promise :as p]))

;; Simple middleware that adds data to the request
(defn wrap-test-middleware [handler]
  (fn [req]
    (handler (assoc req :middleware-executed true))))

;; Another middleware that modifies the response
(defn wrap-response-middleware [handler]
  (fn [req]
    (p/on-realised (handler req)
                   (fn [resolved-response]
                     (p/promise (assoc-in resolved-response [:headers "x-test-middleware"] "executed")))
                   (fn [error]
                     (p/promise {:status 500 :body {:error (str error)}})))))

;; Test handler that returns request data
(defn test-handler [request]
  (p/promise {:status 200
              :headers {"content-type" "application/json"}
              :body {:message "test response"
                     :has-middleware-executed (get request :middleware-executed false)}}))

;; Router configuration with middleware
(def router
  (r/router
   ["/test"
    ["/with-middleware" {:get {:handler test-handler
                               :middleware [wrap-test-middleware]}}]
    ["/with-response-middleware" {:get {:handler test-handler
                                        :middleware [wrap-response-middleware]}}]
    ["/with-both-middleware" {:get {:handler test-handler
                                    :middleware [wrap-test-middleware
                                                 wrap-response-middleware]}}]
    ["/no-middleware" {:get {:handler test-handler}}]
    ["/post-test" {:post {:handler test-handler
                          :middleware [wrap-test-middleware]}}]
    ["/put-test" {:put {:handler test-handler
                        :middleware [wrap-response-middleware]}}]
    ["/delete-test" {:delete {:handler test-handler
                              :middleware [wrap-test-middleware
                                           wrap-response-middleware]}}]
    ["/patch-test" {:patch {:handler test-handler}}]]))

;; Main API handler function (simplified version of your implementation)
;; Create enrich-request function similar to Reitit
(defn create-enrich-request [inject-match? inject-router?]
  (fn [request path-params match router]
    (cond-> request
      inject-match? (assoc :reitit.core/match match)
      inject-router? (assoc :reitit.core/router router)
      path-params (assoc :path-params path-params))))

;; Main API handler function based on Reitit's ring-handler pattern
(defn api-handler [req]
  (let [inject-match? true
        inject-router? true
        enrich-request (create-enrich-request inject-match? inject-router?)]
    (if-let [match (r/match-by-path router (:uri req))]
      (let [method (:request-method req)
            path-params (:path-params match)
            route-data (:data match)  ; Get the route data
            handler (get-in route-data [method :handler])  ; Get handler by HTTP method
            middleware-list (get-in route-data [method :middleware] [])  ; Get middleware by HTTP method
            request (enrich-request req path-params match router)]
        (if handler
          ;; Apply middleware functions to the handler
          ((reduce (fn [h mw-fn] (mw-fn h)) 
                   handler 
                   (reverse middleware-list)) 
           request)
          (p/promise {:status 500
                      :headers {"content-type" "application/json"}
                      :body {:error (str "No handler for method " method " at path " (:uri req))}})))
      (p/promise {:status 404
                  :headers {"content-type" "application/json"}
                  :body {:error "Not found"}}))))

(deftest test-middleware-execution
  (async done
    ;; Test route with middleware
    (let [request {:uri "/test/with-middleware" :request-method :get}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= true (get-in response [:body :has-middleware-executed])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-response-middleware
  (async done
    ;; Test route with response middleware
    (let [request {:uri "/test/with-response-middleware" :request-method :get}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= "executed" (get-in response [:headers "x-test-middleware"])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-multiple-middleware
  (async done
    ;; Test route with multiple middleware
    (let [request {:uri "/test/with-both-middleware" :request-method :get}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= true (get-in response [:body :has-middleware-executed])))
                       (is (= "executed" (get-in response [:headers "x-test-middleware"])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-no-middleware
  (async done
    ;; Test route without middleware
    (let [request {:uri "/test/no-middleware" :request-method :get}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= false (get-in response [:body :has-middleware-executed])))
                       (is (nil? (get-in response [:headers "x-test-middleware"])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-route-matching
  (async done
    ;; Test that route matching works correctly
    (let [request {:uri "/test/non-existent" :request-method :get}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 404 (:status response)))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-post-method
  (async done
    ;; Test POST method
    (let [request {:uri "/test/post-test" :request-method :post}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= true (get-in response [:body :has-middleware-executed])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-put-method
  (async done
    ;; Test PUT method 
    (let [request {:uri "/test/put-test" :request-method :put}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= "executed" (get-in response [:headers "x-test-middleware"])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-delete-method
  (async done
    ;; Test DELETE method
    (let [request {:uri "/test/delete-test" :request-method :delete}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (is (= true (get-in response [:body :has-middleware-executed])))
                       (is (= "executed" (get-in response [:headers "x-test-middleware"])))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

(deftest test-patch-method
  (async done
    ;; Test PATCH method
    (let [request {:uri "/test/patch-test" :request-method :patch}]
      (p/on-realised (api-handler request)
                     (fn [response]
                       (is (= 200 (:status response)))
                       (done))
                     (fn [error]
                       (is (= false true) (str "Request failed with error: " error))
                       (done))))))

;; Run tests
(test/run-tests)
