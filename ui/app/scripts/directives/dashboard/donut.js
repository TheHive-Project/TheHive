(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardDonut', function(StatSrv, $state, DashboardSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                options: '=',
                entity: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                resizeOn: '@',
                metadata: '='
            },
            template: '<c3 chart="chart" resize-on="{{resizeOn}}" error="error"></c3>',
            link: function(scope) {
                scope.error = false;
                scope.chart = {};

                scope.prepareSeriesNames = function() {
                    console.log(scope.entity);
                    console.log(scope.entity.attributes[scope.options.field]);
                    var names = scope.options.names || {};

                    if(scope.entity && scope.options.field) {
                        var field = scope.entity.attributes[scope.options.field];

                        _.each(field.values, function(val, index) {
                            if(!names[val]) {
                                names[val] = field.labels[index] || null;
                            }
                        });
                    }

                    console.log(names);

                    return names;
                };

                scope.load = function() {
                    if(!scope.entity) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var statConfig = {
                        query: query,
                        //objectType: scope.options.entity,
                        objectType: scope.entity.path,
                        field: scope.options.field,
                        //sort: scope.options.sort || '-_count',
                        sort: '+_count',
                        limit: scope.options.limit
                    };

                    scope.options.names = scope.prepareSeriesNames();

                    StatSrv.getPromise(statConfig).then(
                        function(response) {
                            scope.error = false;
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
                            scope.error = true;
                            NotificationSrv.log('Failed to fetch data, please edit the widget definition', 'error');
                        }
                    );
                };

                if (scope.autoload === true) {
                    scope.load();
                }

                if (!_.isEmpty(scope.refreshOn)) {
                    scope.$on(scope.refreshOn, function(event, filter) {
                        scope.filter = filter;
                        scope.load();
                    });
                }
            }
        };
    });
})();
