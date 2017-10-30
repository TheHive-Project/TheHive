(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardLine', function($http, $state, DashboardSrv, NotificationSrv) {
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

                scope.intervals = DashboardSrv.timeIntervals;
                scope.interval = scope.intervals[2];

                scope.load = function() {
                    var query = DashboardSrv.buildChartQuery(scope.options.filter, scope.options.query);

                    var statsPromise = $http.post('./api/' + scope.options.entity + '/_stats', {
                        query: query,
                        stats: [{
                            _agg: 'time',
                            _fields: [scope.options.field],
                            _interval: scope.options.interval || scope.interval.code,
                            _select: _.map(scope.options.series || [], function(serie) {
                                var s = {_agg: serie.agg};

                                if(serie.agg !== 'count') {
                                    s._field = serie.field;
                                }

                                return s;
                            })
                        }]
                    });

                    statsPromise.then(function(response) {
                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d, 'YYYYMMDDTHHmmZZ').format('YYYY-MM-DD');
                        });

                        var columns = [];
                        var values = _.pluck(_.values(response.data), scope.options.field);

                        scope.types = {};
                        scope.names = {};
                        scope.axes = {};
                        _.each(scope.options.series, function(serie) {
                            var key = serie.field,
                                agg = serie.agg,
                                dataKey = agg === 'count' ? 'count' : (agg + '_' + key),
                                columnKey = key + '.' + agg;

                            columns.push([columnKey].concat(_.pluck(values, dataKey)));

                            scope.types[columnKey] = serie.type || 'line';
                            scope.names[columnKey] = agg === 'count' ? 'count' : (agg + ' of ' + key);
                            scope.axes[columnKey] = (scope.types[columnKey] === 'bar') ? 'y2' : 'y';
                        });

                        var chart = {
                            data: {
                                x: 'date',
                                columns: [
                                    ['date'].concat(labels)
                                ].concat(columns),
                                names: scope.names || {},
                                type: scope.type || 'bar',
                                types: scope.types || {},
                                axes: scope.axes || {}
                            },
                            bar: {
                                width: {
                                    ratio: 0.1
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
                        NotificationSrv.error('dashboardLine', err.data, err.status);
                    });
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
                // if (!_.isEmpty(scope.resizeOn)) {
                //     scope.$on(scope.resizeOn, function() {
                //         scope.chart.resize();
                //     });
                // }
            }
        };
    });
})();
