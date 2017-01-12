(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(StatSrv, $q) {

            this.fromCases = function(query) {
                var defer = $q.defer();

                StatSrv.getPromise({
                    objectType: 'case',
                    field: 'tags',
                    limit: 1000
                }).then(function(response) {
                    var tags = [];

                    tags = _.map(_.filter(_.keys(response.data), function(tag) {
                        var regex = new RegExp(query, 'gi');
                        return regex.test(tag);
                    }), function(tag) {
                        return {text: tag};
                    });

                    defer.resolve(tags);
                });

                return defer.promise;
            };

        });
})();
