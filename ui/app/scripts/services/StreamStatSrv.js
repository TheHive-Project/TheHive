(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('StreamStatSrv', function($http, StreamSrv, StatSrv) {
            /**
             * @param config {Object} This configuration object has the following attributes
             * <li>rootId</li>
             * <li>query {Object}</li>
             * <li>objectType {String}</li>
             * <li>field {String}</li>
             * <li>result {Object}</li>
             * <li>success {Function}</li>
             * <li>error {Function}</li>
             */
            return function(config) {
                StreamSrv.listen(config.rootId, config.objectType, function() {
                    StatSrv.get(config);
                });

                return StatSrv.get(config);
            };
        });
})();
