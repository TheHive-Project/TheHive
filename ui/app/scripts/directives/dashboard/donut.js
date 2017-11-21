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
            template: '<c3 chart="chart" resize-on="{{resizeOn}}" error="error" on-save-csv="getCsv()"></c3>',
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
                                names[val] = field.labels[index] || val;
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

                        objectType: scope.entity.path,
                        field: scope.options.field,
                        sort: scope.options.sort ? [scope.options.sort] : '-_count',
                        limit: scope.options.limit || 10
                    };

                    scope.options.names = scope.prepareSeriesNames();

                    StatSrv.getPromise(statConfig).then(
                        function(response) {
                            scope.error = false;
                            var data = {};
                            var total = response.data.count;

                            delete response.data.count;

                            _.each(response.data, function(val, key) {
                                data[key] = val.count;
                            });

                            scope.data = data;

                            scope.chart = {
                                data: {
                                    json: scope.data,
                                    type: 'donut',
                                    names: scope.options.names || {},
                                    colors: scope.options.colors || {},
                                    // onclick: function(d) {
                                    //     var criteria = [{ _type: scope.options.entity }, { _field: scope.options.field, _value: d.id }];
                                    //
                                    //     if (scope.options.query && scope.options.query !== '*') {
                                    //         criteria.push(scope.options.query);
                                    //     }
                                    //
                                    //     var searchQuery = {
                                    //         _and: criteria
                                    //     };
                                    //
                                    //     $state.go('app.search', {
                                    //         q: Base64.encode(angular.toJson(searchQuery))
                                    //     });
                                    // }
                                },
                                donut: {
                                    title: 'Total: ' + total,
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

                scope.getCsv = function() {
                    var csv = [];
                    _.each(scope.data, function(val, key) {
                        csv.push({data: key  + ';' + val});
                    });
                    return csv;
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
