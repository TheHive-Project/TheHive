(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardDonut', function(StatSrv, $state, DashboardSrv, NotificationSrv, GlobalSearchSrv) {
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
                    if(!scope.options.field) {
                        return {};
                    }

                    var field = scope.entity.attributes[scope.options.field];

                    if(field.values.length === 0) {
                        // This is not an enumerated field
                        // Labels and colors customization is not available
                        return {};
                    }

                    var names = scope.options.names || {};

                    _.each(field.values, function(val, index) {
                        if(!names[val]) {
                            names[val] = field.labels[index] || val;
                        }
                    });

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
                                    onclick: function(d) {
                                        if(scope.mode === 'edit') {
                                            return;
                                        }

                                        var fieldDef = scope.entity.attributes[scope.options.field];

                                        var data = {
                                            field: scope.options.field,
                                            type: fieldDef.type,
                                            value: GlobalSearchSrv.buildDefaultFilterValue(fieldDef, d)
                                        };

                                        GlobalSearchSrv.saveSection(scope.options.entity, {
                                            search: null,
                                            filters: scope.options.filters.concat([data])
                                        });
                                        $state.go('app.search');
                                    }
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
                        function(/*err*/) {
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
