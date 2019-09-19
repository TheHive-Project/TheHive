(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(StatSrv, $q) {

            var getPromiseFor = function(objectType) {
                return StatSrv.getPromise({
                    objectType: objectType,
                    field: 'tags',
                    limit: 1000
                });
            };

            var mapTags = function(collection, term) {
                return _.map(_.filter(_.keys(collection), function(tag) {
                    var regex = new RegExp(term, 'gi');
                    return regex.test(tag);
                }), function(tag) {
                    return {text: tag};
                });
            };

            var getTags = function(objectType, term) {
                var defer = $q.defer();

                getPromiseFor(objectType).then(function(response) {
                    defer.resolve(mapTags(response.data, term) || []);
                });

                return defer.promise;
            };

            this.fromCases = function(term) {
                return getTags('case', term);
            };

            this.fromAlerts = function(term) {
                return getTags('alert', term);
            };

            this.fromObservables = function(term) {
                return getTags('case/artifact', term);
            };

        });
})();
