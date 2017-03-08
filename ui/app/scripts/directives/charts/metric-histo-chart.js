(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('metricHistoChart', function($http, $interpolate, MetricsCacheSrv, ChartSrv, AlertSrv) {
        return {
            restrict: 'E',
            scope: {
                'autoload': '=',
                'options': '=',
                'refreshOn': '@'
            },
            templateUrl: 'views/directives/charts/metric-histo-chart.html',
            link: function(scope) {
                scope.chart = {};
                scope.allAggregations = ChartSrv.aggregations;
                scope.intervals = ChartSrv.timeIntervals;
                scope.interval = scope.intervals[2];
                scope.selectedMetrics = [];
                scope.selectedAggregations = [];

                scope.buildQuery = function() {
                    var criteria = _.without([
                        scope.options.filter,
                        scope.options.query
                    ], null, undefined, '', '*');

                    return criteria.length === 1 ? criteria[0] : {_and: criteria};
                };

                scope.getSelectors = function() {
                    var selectors = [];

                    _.each(scope.options.metrics, function(m) {
                        _.each(scope.options.aggregations, function(a) {
                            selectors.push({
                                _agg: a,
                                _field: m
                            });
                        });
                    });

                    return selectors;
                };

                scope.load = function() {
                    var options = {
                        params: {
                            entity: scope.options.entity,
                            field: scope.options.field,
                            duration: scope.interval.code,
                            q: scope.buildQuery(),
                            metrics: scope.options.metrics.sort()
                        }
                    };

                    scope.columnKeys = [];

                    $http.post('./api/' + options.params.entity + '/_stats', {
                        "query": options.params.q,
                        "stats": [{
                            "_agg": "time",
                            "_fields": [options.params.field],
                            "_interval": options.params.duration,
                            "_select": scope.getSelectors()
                        }]
                    }).then(function(response) {
                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d, 'YYYYMMDDTHHmmZZ').format('YYYY-MM-DD');
                        });

                        var columns = [];
                        var values = _.pluck(_.values(response.data), options.params.field);

                        _.each(scope.options.metrics, function(metric) {
                            _.each(scope.options.aggregations, function(agg) {
                                scope.columnKeys.push(metric + '.' + agg);
                                columns.push([metric + '.' + agg].concat(_.pluck(values, agg + '_' + metric)));
                            });
                        });

                        scope.names = {};
                        scope.axes = {};
                        scope.types = {};
                        _.each(scope.columnKeys, function(ck) {
                            var segs = ck.replace('metrics.', '').split('.');
                            scope.names[ck] = segs[1] + ' of ' + segs[0];
                            scope.axes[ck] = (segs[1] === 'count') ? 'y2' : 'y';
                            scope.types[ck] = (segs[1] === 'count') ? 'bar' : (scope.type || 'line');
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
                                    show: scope.options.aggregations.indexOf('count') !== -1
                                }
                            },
                            zoom: {
                                enabled: scope.options.zoom || false
                            }
                        };


                        scope.chart = chart;
                    }, function(err) {
                        AlertSrv.error('metricHistoChart', err.data, err.status);
                    });
                };

                // Load all metrics
                MetricsCacheSrv.all().then(function(metrics) {
                    var keys = [];

                    // Get all metrics
                    scope.allMetrics = _.keys(metrics).map(function(key) {
                        keys.push('metrics.' + key);
                        var metric = metrics[key];

                        return {
                            id: 'metrics.' + metric.name,
                            label: metric.title
                        };
                    });

                    // If no metrics have been specified in the options, use all metrics
                    if (!scope.options.metrics || scope.options.metrics.length === 0) {
                        scope.options.metrics = keys;
                    }

                    // Prepare the data for the metrics filter dropdown
                    scope.selectedMetrics = scope.options.metrics ? _.map(scope.options.metrics, function(m) {
                        return {
                            id: m
                        };
                    }) : [];

                    // Prepare the data for the aggregations filter dropdown
                    scope.selectedAggregations = scope.options.aggregations ? _.map(scope.options.aggregations, function(agg) {
                        return {
                            id: agg
                        };
                    }) : [];

                    scope.$watchCollection('selectedMetrics', function() {
                        scope.options.metrics = _.pluck(scope.selectedMetrics, 'id');
                    });
                    scope.$watchCollection('selectedAggregations', function() {
                        scope.options.aggregations = _.pluck(scope.selectedAggregations, 'id');
                    });

                    // Run the first chart load
                    if (scope.autoload === true) {
                        scope.load();
                    }
                });

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
