(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardCategory', function($http, $state, DashboardSrv, NotificationSrv) {
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

                scope.xAxisFieldUpdate = function($event) {
                    console.log('xAxisFieldUpdate', $event);
                };

                scope.buildY = function() {
                    var yAxis = scope.options.yAxis,
                        agg = {
                            _agg: yAxis.type
                        };

                    if(yAxis.type === 'field') {
                        agg._field = yAxis.field;
                        agg._select = [{
                            _agg: 'count',
                            _name: 'count'
                        }];
                    }

                    return agg;
                };

                scope.buildStats = function() {
                    var xAxis = scope.options.xAxis,
                        agg = {
                            _agg: xAxis.type
                        };

                    if(xAxis.type === 'field') {
                        agg._field = xAxis.field;
                        agg._select = [
                            scope.buildY()
                        ];
                    }

                    return agg;
                };

                scope.load = function() {
                    if(!scope.entity) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var stats = {
                        model: scope.options.entity,
                        query: query,
                        stats: [scope.buildStats()]
                    };

                    console.log(stats);

                    var statsPromise = $http.post('./api/_stats', {
                        stats: [stats]
                    });

                    statsPromise.then(function(response) {
                        scope.error = false;

                        var xValues = Object.keys(response.data).sort();
                        var data = [];
                        var yValues = [];
                        var yLabels = [];
                        var yColors = {};
                        xValues.forEach(function(item) {
                            //var row = { name: item };
                            var row = { name: (scope.options.xAxis.names || {})[item] || item };

                            Object.keys(response.data[item]).forEach(function(key) {
                                var keyLabel = (scope.options.yAxis.names || {})[key] || key;

                                row[keyLabel] = response.data[item][key].count;

                                if(yValues.indexOf(keyLabel) === -1) {
                                    yValues.push(keyLabel);

                                    yColors[keyLabel] = (scope.options.yAxis.colors || {})[key];
                                }
                            });

                            data.push(row);
                        });

                        console.log(data);
                        console.log(yValues);
                        console.log(yLabels);
                        console.log(yColors);


                        scope.data = data;

                        var chart = {
                            data: {
                                //x: '_date',
                                json: scope.data,
                                type: 'bar',
                                keys: {
                                    x: 'name',
                                    value: yValues
                                },
                                groups: [yValues],
                                colors: yColors
                            },
                            axis: {
                                x: {
                                    type: 'category'
                                }
                            }
                        };

                        scope.chart = chart;
                    }, function(/*err*/) {
                        scope.error = true;
                        NotificationSrv.log('Failed to fetch data, please edit the widget definition', 'error');
                    });
                };

                scope.getCsv = function() {
                    var dates = scope.data._date;
                    var keys = _.keys(scope.data);
                    var headers = _.extend({_date: 'Date'}, scope.names);

                    var csv = [{data: _.map(keys, function(key){
                        return headers[key] || key;
                    }).join(';')}];

                    var row = [];
                    for(var i=0; i<dates.length; i++) {
                        row = _.map(keys, function(key) {
                            return scope.data[key][i];
                        });

                        csv.push({data: row.join(';')});
                    }

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
