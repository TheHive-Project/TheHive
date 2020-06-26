(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('QuerySrv', function($http, $q) {
            var self = this;

            this._buildSort = function(sort) {

                var input = _.isArray(sort) ? sort : [sort];

                return _.map(input, function(item) {
                    var ret = {};

                    if(item.startsWith('-')) {
                        ret[item.slice(1)] = 'desc';
                    } else if (item.startsWith('+')) {
                        ret[item.slice(1)] = 'asc';
                    }

                    return ret;
                });
            };

            this.query = function(version, operations, config) {
                return $http.post('./api/' + version + '/query', {
                    query: operations
                }, config || {});
            };


            this.call = function(version, selectorOperation, options) {
                var operations = [].concat(selectorOperation);

                // Apply filter is defined
                if (options && options.filter) {
                    operations.push(
                        _.extend({'_name': 'filter'}, options.filter)
                    );
                }

                // Apply sort is defined
                if (options && options.sort) {
                    operations.push(
                        _.extend({'_name': 'sort'}, {'_fields': this._buildSort(options.sort)})
                    );
                }

                return self.query(version, operations)
                    .then(function(response) {
                        return $q.resolve(response.data);
                    });
            };
        });
})();
