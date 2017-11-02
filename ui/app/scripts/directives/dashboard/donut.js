(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardDonut', function(StatSrv, $state, DashboardSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                options: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                resizeOn: '@',
                metadata: '='
            },
            template: '<c3 chart="chart" resize-on="{{resizeOn}}"></c3>',
            link: function(scope) {
                scope.chart = {};

                scope.load = function() {
                    if(!scope.options.entity) {
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.options.filter, scope.options.query);

                    var statConfig = {
                        query: query,
                        objectType: scope.options.entity,
                        field: scope.options.field,
                        sort: scope.options.sort,
                        limit: scope.options.limit
                    };

                    StatSrv.getPromise(statConfig).then(
                        function(response) {
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
                                        var criteria = [{ _type: scope.options.entity }, { _field: scope.options.field, _value: d.id }];

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
                        },
                        function(err) {
                            NotificationSrv.error('donutChart', err.data, err.status);
                        }
                    );
                };

                if (scope.autoload === true) {
                    scope.load();
                }

                if (!_.isEmpty(scope.refreshOn)) {
                    scope.$on(scope.refreshOn, function(event, queryFn) {
                        // TODO nadouani: double check when the queryFn is needed
                        //scope.options.query = queryFn(scope.options);
                        scope.load();
                    });
                }
            }
        };
    });
})();
