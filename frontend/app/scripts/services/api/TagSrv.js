(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(QuerySrv, $q) {

            var self = this;

            var getTags = function(objectType, term) {
                var defer = $q.defer();
                var operations = [
                    { _name: 'listTag' }
                ];

                if(objectType) {
                    operations.push({ _name: objectType });
                }

                operations.push({
                    _name: 'filter',
                    _like: {
                        _field: 'text',
                        _value: '*' + term + '*'
                    }
                });

                operations.push({ _name: 'text' });

                // Get the list
                QuerySrv.call('v0', operations, {name: 'tags-auto-complete'})
                    .then(function(data) {
                        defer.resolve(_.map(_.unique(data), function(tag) {
                            return {text: tag};
                        }));
                    });

                return defer.promise;
            };

            this.getTagsFor = function(entity, query) {

                switch(entity) {
                    case 'case':
                        return self.fromCases(query);
                    case 'observable':
                        return self.fromObservables(query);
                    case 'alert':
                        return self.fromAlerts(query);
                    default:
                        return self.getTags(undefined, query);
                }

            };

            this.fromCases = function(term) {
                return getTags('fromCase', term);
            };

            this.fromObservables = function(term) {
                return getTags('fromObservable', term);
            };

            this.fromAlerts = function(term) {
                return getTags('fromAlert', term);
            };

        });
})();
