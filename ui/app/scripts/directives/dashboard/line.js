(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardLine', function($http, $state, DashboardSrv, NotificationSrv) {
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
                            _fields: [scope.options.field],
                            _interval: scope.options.interval || scope.interval.code,
                            _select: _.map(scope.options.series || [], function(serie, index) {
                                var s = {
                                    _agg: serie.agg,
                                    _name: 'agg_' + (index + 1),
                                    _query: serie.query || {}
                                };

                                if(serie.agg !== 'count') {
                                    s._field = serie.field;
                                }

                                return s;
                            })
                        }]
                    });

                    statsPromise.then(function(response) {
                        scope.error = false;
                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d * 1).format('YYYY-MM-DD');
                        });
                        var len = labels.length;

                        var columns = [];
                        var values = _.pluck(_.values(response.data), scope.options.field);

                        scope.types = {};
                        scope.names = {};
                        scope.axes = {};
                        scope.colors = {};
                        _.each(scope.options.series, function(serie, index) {
                            var key = serie.field,
                                agg = serie.agg,
                                dataKey = agg === 'count' ? 'count' : (agg + '_' + key),
                                columnKey = 'agg_' + (index + 1);

                            columns.push([columnKey].concat(_.pluck(values, columnKey)));

                            scope.types[columnKey] = serie.type || 'line';
                            scope.names[columnKey] = serie.label || (agg === 'count' ? 'count' : (agg + ' of ' + key));
                            scope.axes[columnKey] = (scope.types[columnKey] === 'bar') ? 'y2' : 'y';
                            scope.colors[columnKey] = serie.color;
                        });

                        // Compute stack groups
                        var groups = {};
                        _.each(scope.types, function(value, key) {
                            if (groups[value]) {
                                groups[value].push(key);
                            } else {
                                groups[value] = [key];
                            }
                        });
                        scope.groups = scope.options.stacked === true ? _.values(groups) : {};

                        scope.data = [
                            ['date'].concat(labels)
                        ].concat(columns);

                        console.log('Line data:', scope.data);

                        var chart = {
                            data: {
                                x: 'date',
                                columns: scope.data,
                                names: scope.names || {},
                                type: scope.type || 'bar',
                                types: scope.types || {},
                                axes: scope.axes || {},
                                colors: scope.colors || {},
                                groups: scope.groups || []
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
                                },
                                y2: {
                                    show: _.values(scope.axes).indexOf('y2') !== -1
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

                scope.getCsv = function() {
                    
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
