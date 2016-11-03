(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('durationOverTimeChart', function($http, $interpolate, ChartSrv) {
        return {
            restrict: 'E',
            scope: {
                'autoload': '=',
                'options': '=',
                'refreshOn': '@'
            },
            templateUrl: 'views/directives/charts/duration-over-time-chart.html',
            link: function(scope) {
                scope.chart = {};
                scope.intervals = ChartSrv.timeIntervals;
                scope.interval = scope.intervals[2];

                scope.buildQuery = function() {
                    var criteria = _.without([
                        scope.options.filter,
                        scope.options.query
                    ], null, undefined, '', '*');

                    return criteria.length === 1 ? criteria[0] : {_and: criteria};
                };

                scope.load = function() {
                    var options = {
                        params: {
                            q: scope.buildQuery(),
                            duration: scope.interval.code
                        }
                    };

                    var computedFieldName = 'computed.handlingDuration';

                    $http.post('/api/case/_stats', {
                        query: options.params.q,
                        stats: [{
                            _agg: 'time',
                            _fields: [scope.options.dateField],
                            _interval: options.params.duration,
                            _select: [{
                                _agg: 'avg',
                                _field: computedFieldName
                            },
                            {
                                _agg: 'min',
                                _field: computedFieldName
                            },
                            {
                                _agg: 'max',
                                _field: computedFieldName
                            },
                            {
                                _agg: 'count'
                            }]
                        }]
                    }).then(function(response) {
                        //var fieldNames =

                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d, 'YYYYMMDDTHHmmZZ').format('YYYY-MM-DD');
                        });

                        var fn = function(value) {
                            return moment.duration(value).asDays();
                        };

                        var humanDuration = function(value) {
                            var days = Math.round(value);

                            if (days === 0) {
                                return '< 1 day';
                            }
                            return days + ' day' + (days > 1 ? 's' : '');
                        };

                        var data = _.pluck(_.values(response.data), scope.options.dateField);

                        var count = _.pluck(data, 'count');
                        var max = _.pluck(data, 'max_' + computedFieldName).map(fn);
                        var min = _.pluck(data, 'min_' + computedFieldName).map(fn);
                        var avg = _.pluck(data, 'avg_' + computedFieldName).map(fn);

                        scope.chart = {
                            data: {
                                x: 'date',
                                columns: [
                                    ['date'].concat(labels), ['count'].concat(count), ['max'].concat(max), ['min'].concat(min), ['avg'].concat(avg)
                                ],
                                names: scope.options.names || {},
                                type: 'line',
                                types: scope.options.types || {},
                                axes: scope.options.axes || {},
                                colors: scope.options.colors || {}
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
                                        rotate: 90
                                    }
                                },
                                y2: {
                                    show: true
                                }
                            },
                            tooltip: {
                                format: {
                                    value: function(value, ratio, id) {
                                        if (['min', 'max', 'avg'].indexOf(id) !== -1) {
                                            return humanDuration(value);
                                        }
                                        return value;
                                    }

                                }
                            },
                            zoom: {
                                enabled: scope.zoom || false
                            }
                        };

                    }, function(response) {
                        console.error(response);
                    });
                };

                if (scope.autoload === true) {
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
