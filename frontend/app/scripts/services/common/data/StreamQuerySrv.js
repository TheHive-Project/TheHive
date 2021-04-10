(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('StreamQuerySrv', function ($http, StreamSrv, QuerySrv) {
            return function (version, operations, config) {
                StreamSrv.addListener({
                    scope: config.scope,
                    rootId: config.rootId,
                    objectType: config.objectType,
                    callback: function (updates) {
                        if (!config.guard || config.guard(updates)) {
                            QuerySrv.query(version, operations, config.query)
                                .then(function (response) {
                                    config.onUpdate(response.data);
                                });
                        }
                    }
                });

                QuerySrv.query(version, operations, config.query)
                    .then(function (response) {
                        config.onUpdate(response.data);
                    });
            };
        });
})();
