(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('donutChart', function(StatSrv, $state, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                'options': '=',
                'autoload': '=',
                'mode': '=',
                'refreshOn': '@'
            },
            templateUrl: 'views/directives/charts/donut-chart.html',
            link: function(scope) {
                scope.chart = {};

                scope.buildQuery = function() {
                    var criteria = _.without([
                        scope.options.filter,
                        scope.options.query
                    ], null, undefined, '', '*');

                    return criteria.length === 1 ? criteria[0] : {_and: criteria};
                };

                scope.load = function() {
                    var query = scope.buildQuery();

                    var statConfig = {
                        query: query,
                        objectType: scope.options.type,
                        field: scope.options.field,
                        sort: scope.options.sort,
                        limit: scope.options.limit
                    };

                    StatSrv.getPromise(statConfig).then(function(response) {

                        var keys = _.without(_.keys(response.data), 'count');
                        var columns = keys.map(function(key) {
                            return [key, response.data[key].count];
                        });

                        scope.chart = {
                            data: {
                                columns: columns,
                                type: 'donut',
                                names: scope.options.names || {},
                                colors: scope.options.colors || {},
                                onclick: function(d) {
                                    var criteria = [
                                        { _type : scope.options.type },
                                        { _field: scope.options.field, _value: d.id}
                                    ];

                                    if (scope.options.query && scope.options.query !== '*') {
                                        criteria.push(scope.options.query);
                                    }

                                    var searchQuery = {
                                        _and: criteria
                                    };

                                    $state.go('app.search', {
                                        q: Base64.encode(angular.toJson(searchQuery))
                                    });
                                }
                            },
                            donut: {
                                title: 'Total: ' + response.data.count,
                                label: {
                                    format: function(value) {
                                        return value;
                                    }
                                }
                            }
                        };

                    }, function(err) {
                        NotificationSrv.error('donutChart', err.data, err.status);
                    });
                };

                if(scope.autoload === true) {
                    scope.load();
                }

                if (!_.isEmpty(scope.refreshOn)) {
                    scope.$on(scope.refreshOn, function(event, queryFn) {
                        scope.options.query = queryFn(scope.options);
                        scope.load();
                    });
                }
            }

        };
    });

})();
