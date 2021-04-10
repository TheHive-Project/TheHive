(function () {
    'use strict';
    angular.module('theHiveServices')
        .service('QuerySrv', function ($http, $q) {
            var self = this;

            this._buildSort = function (sort) {

                var input = _.isArray(sort) ? sort : [sort];

                return _.map(input, function (item) {
                    var ret = {};

                    if (item.startsWith('-')) {
                        ret[item.slice(1)] = 'desc';
                    } else if (item.startsWith('+')) {
                        ret[item.slice(1)] = 'asc';
                    }

                    return ret;
                });
            };

            this.query = function (version, operations, config) {
                return $http.post('./api/' + version + '/query', {
                    query: operations
                }, config || {});
            };


            /**
             * List objects using the Query API
             *
             * @param  {type} version           API version
             * @param  {type} selectorOperation Initial operations
             * @param  {type} options           Options object defining filters, sort, pagination and addition $http options
             * @return {type}                   Promise resolving to the list of the searched objects
             */
            this.call = function (version, selectorOperation, options) {
                var operations = [].concat(selectorOperation);
                var config = {};

                // Apply filter is defined
                if (options && options.filter && !_.isEmpty(options.filter)) {
                    operations.push(
                        _.extend({ '_name': 'filter' }, options.filter)
                    );
                }

                // Apply sort is defined
                if (options && options.sort) {
                    operations.push(
                        _.extend({ '_name': 'sort' }, { '_fields': this._buildSort(options.sort) })
                    );
                }

                // Apply pagination if isDefined
                if (options && options.page) {
                    operations.push(
                        _.extend({ '_name': 'page' }, options.page)
                    );
                }

                if (options && options.name) {
                    config.params = {
                        name: options.name
                    };
                }

                if (options && options.config) {
                    config = _.extend({}, config, options.config);
                }

                return self.query(version, operations, config)
                    .then(function (response) {
                        return $q.resolve(response.data);
                    });
            };


            /**
             * Count objects using the Query API
             *
             * @param  {type} version           API version
             * @param  {type} selectorOperation Initial operations
             * @param  {type} options           Options object defining filters and addition $http options
             * @return {type}                   Promise resolving to the total of the searched objects
             */
            this.count = function (version, selectorOperation, options) {
                var operations = [].concat(selectorOperation);
                var config = {};

                // Apply filter is defined
                if (options && options.filter && !_.isEmpty(options.filter)) {
                    operations.push(
                        _.extend({ '_name': 'filter' }, options.filter)
                    );
                }

                if (options && options.name) {
                    config.params = {
                        name: options.name + '.count'
                    };
                }

                if (options && options.config) {
                    config = _.extend({}, config, options.config);
                }

                // Add count
                operations.push({ '_name': options.limitedCount ? 'limitedCount' : 'count' });

                return self.query(version, operations, config)
                    .then(function (response) {
                        return $q.resolve(response.data);
                    });
            };
        });
})();
