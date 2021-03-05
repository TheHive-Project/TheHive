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
                        name: 'list-tags'
                    }
                }).then(function(response) {
                    defer.resolve(_.map(response.data, function(tag) {
                        return {text: tag};
                    }));
                });

                return defer.promise;
            };

            this.autoComplete = function(term) {
                var defer = $q.defer();

                var operations = [
                    { _name: 'tagAutoComplete', freeTag: term, limit: 20}
                ]

                QuerySrv.call('v1', operations, {
                    name: 'tags-auto-complete'
                }).then(function(response) {
                    defer.resolve(_.map(response, function(tag) {
                        return {text: tag};
                    }));
                });

                return defer.promise;
            };

        });
})();
