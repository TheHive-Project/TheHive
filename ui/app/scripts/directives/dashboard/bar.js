(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardBar', function($http, $state, DashboardSrv, NotificationSrv) {
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

                scope.intervals = DashboardSrv.timeIntervals;
                scope.interval = scope.intervals[2];

                scope.load = function() {
                    if(!scope.entity) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var statsPromise = $http.post('./api' + scope.entity.path + '/_stats', {
                        query: query,
                        stats: [{
                            _agg: 'time',
                            _fields: [scope.options.dateField],
                            _interval: scope.options.interval || scope.interval.code,
                            _select: [{
                                _agg: 'field',
                                _field: scope.options.field,
                                _select: [{
                                    _agg: 'count'
                                }]
                            }]
                        }]
                    });

                    statsPromise.then(function(response) {
                        scope.error = false;
                        var len = _.keys(response.data).length,
                            data = {_date: (new Array(len)).fill(0)};

                        var rawData = {};
                        _.each(response.data, function(value, key) {
                            rawData[key] = value[scope.options.dateField]
                        });

                        _.each(rawData, function(value) {
                            _.each(_.keys(value), function(key){
                                data[key] = (new Array(len)).fill(0);
                            });
                        });

                        var i = 0;
                        _.each(rawData, function(value, key) {
                            data._date[i] = moment(key * 1).format('YYYY-MM-DD');

                            _.each(_.keys(value), function(item) {
                                data[item][i] = value[item].count;
                            });

                            i++;
                        });

                        scope.names = {};
                        scope.colors = {};

                        var chart = {
                            data: {
                                x: '_date',
                                json: data,
                                type: 'bar',
                                //names: scope.names || {},
                                //colors: scope.colors || {},
                                groups: scope.options.stacked === true ? [_.without(_.keys(data), '_date')] : []
                            },
                            bar: {
                                width: {
                                    ratio: 1 - Math.exp(-len/20)
                                }
                            },
                            axis: {
                                x: {
                                    type: 'timeseries',
                                    tick: {
                                        format: '%Y-%m-%d',
                                        rotate: 90,
                                        height: 50
                                    }
                                }
                            },
                            zoom: {
                                enabled: scope.options.zoom || false
                            }
                        };

                        scope.chart = chart;
                    }, function(err) {
                        scope.error = true;
                        NotificationSrv.log('Failed to fetch data, please edit the widget definition', 'error');
                    });
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
