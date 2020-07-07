(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(QuerySrv, $q) {

            var getTags = function(objectType, term) {
                var defer = $q.defer();

                var operations = [
                    { _name: 'listTag' },
                    { _name: objectType },
                    {
                        _name: 'filter',
                        _like: {
                            _field: 'text',
                            _value: '*' + term + '*'
                        }
                    },
                    {
                        _name: 'text'
                    }
                ];

                // Get the list
                QuerySrv.call('v0', operations)
                    .then(function(data) {                        
                        defer.resolve(_.map(_.unique(data), function(tag) {
                            return {text: tag};
                        }));
                    });

                return defer.promise;
            };

            this.fromCases = function(term) {
                return getTags('fromCase', term);
            };

            this.fromObservables = function(term) {
                return getTags('fromObservable', term);
            };

        });
})();
