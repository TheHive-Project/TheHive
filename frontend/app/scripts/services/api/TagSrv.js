(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('TagSrv', function(QuerySrv, $q) {

            var self = this;

            this.getTags = function(term) {
                var defer = $q.defer();

                var operations = [
                    { _name: 'listTag'},
                    { _name: 'freetags'},
                    { _name: 'filter', _like: {_field: 'predicate', _value: term+'*'}},
                    { _name: 'sort', _fields: [{'predicate': 'asc'}]},
                    { _name: 'text'},
                    // { _name: 'page', from: 0, to: 20},
                    // { _name: 'text'}
                ]

                QuerySrv.query('v1', operations, {
                    params: {
                        name: 'tags-auto-complete'
                    }
                }).then(function(response) {
                    defer.resolve(_.map(response.data, function(tag) {
                        return {text: tag};
                    }));
                });

                return defer.promise;
            };

        });
})();
