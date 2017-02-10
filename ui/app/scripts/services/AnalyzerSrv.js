(function () {
    'use strict';
    angular.module('theHiveServices')
        .factory('AnalyzerSrv', function ($resource, $q) {
            var analyzers = null,
                resource = $resource('./api/connector/cortex/analyzer/:analyzerId', {}, {
                    query: {
                        method: 'GET',
                        url: './api/connector/cortex/analyzer',
                        isArray: true
                    },
                    get: {
                        isArray: true
                    },
                    update: {
                        method: 'PATCH'
                    }
                });

            var factory = {
                clearCache: function () {
                    analyzers = null;
                },
                query: function () {
                    var deferred = $q.defer();

                    if (analyzers === null) {

                        resource.query({
                            range: 'all'
                        }, {}, function (response) {

                            analyzers = _.indexBy(_.map(response, function(item) {
                                return item.toJSON();
                            }), 'id');

                            deferred.resolve(analyzers);
                        }, function (/*rejection*/) {
                            deferred.reject({});
                        });

                    } else {
                        deferred.resolve(analyzers);
                    }

                    return deferred.promise;
                },

                get: function(analyzerId) {
                    var deferred = $q.defer();

                    if(analyzers !== null && analyzers[analyzerId]) {
                        deferred.resolve(analyzers[analyzerId]);
                    } else {
                        resource.get({
                            'analyzerId': analyzerId
                        }, function (data) {
                            deferred.resolve(data);
                        }, function (rejection) {
                            deferred.reject(rejection);
                        });
                    }

                    return deferred.promise;
                },

                forDataType: function(dataType) {
                    var deferred = $q.defer();

                    factory.query()
                        .then(function(all) {
                            var filtered = {};
                            _.each(all, function(value, key) {
                                if(value.dataTypeList && value.dataTypeList.indexOf(dataType) !== -1) {
                                    filtered[key] = angular.copy(value);
                                    filtered[key].active = true;
                                }
                            });

                            deferred.resolve(filtered);
                        });

                    return deferred.promise;
                },

                serversFor: function(analyzerIds) {
                    var deferred = $q.defer();

                    factory.query()
                        .then(function(all) {
                            var cortexIds = [];

                            _.each(all, function(value, key) {
                                if(analyzerIds.indexOf(key) > -1){
                                    cortexIds = cortexIds.concat(value.cortexIds);
                                }
                            });

                            deferred.resolve(_.uniq(cortexIds));
                        });

                    return deferred.promise;
                }
            };

            return factory;
        });
})();
