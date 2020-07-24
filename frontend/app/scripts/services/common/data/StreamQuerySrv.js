(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('StreamQuerySrv', function($http, StreamSrv, QuerySrv) {
            return function(version, operations, config) {
                StreamSrv.addListener({
                    rootId: config.rootId,
                    objectType: config.objectType,
                    scope: config.scope,
                    callback:function() {
                        QuerySrv.query(version, operations, config.query)
                            .then(function(response) {
                                config.onUpdate(response.data);
                            });
                    }
                });

                QuerySrv.query(version, operations, config.query)
                    .then(function(response) {
                        config.onUpdate(response.data);
                    });
            };
        });
})();
