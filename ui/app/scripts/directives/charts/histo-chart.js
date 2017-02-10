(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('histoChart', function($http, ChartSrv) {
        return {
            restrict: 'E',
            scope: {
                'autoload': '=',
                'options': '=',
                'refreshOn': '@'
            },
            templateUrl: 'views/directives/charts/histo-chart.html',
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

                scope.getCountFn = function(val) {
                    return val.count || 0;
                };

                scope.load = function() {
                    var options = {
                        params: {
                            entity: scope.options.type,
                            fields: scope.options.fields,
                            q: scope.buildQuery(),
                            duration: scope.interval.code
                        }
                    };

                    $http.post('./api/' + options.params.entity + '/_stats', {
                        "query": options.params.q,
                        "stats": [{
                            "_agg": "time",
                            "_fields": options.params.fields,
                            "_interval": options.params.duration,
                            "_select": [{
                                "_agg": "count"
                            }]
                        }]
                    }).then(function(response) {
                        var labels = _.keys(response.data).map(function(d) {
                            return moment(d, 'YYYYMMDDTHHmmZZ').format('YYYY-MM-DD');
                        });

                        var values = _.values(response.data);
                        var columns = _.map(scope.options.fields, function(field) {
                            var fieldValues = _.pluck(values, field);

                            return [field].concat(_.map(fieldValues, scope.getCountFn));
                        });

                        scope.chart = {
                            data: {
                                x: 'date',
                                columns: [
                                    ['date'].concat(labels)
                                ].concat(columns),
                                names: scope.options.names || {},
                                type: 'bar',
                                types: scope.options.types || {}
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
                                }
                            },
                            zoom: {
                                enabled: scope.options.zoom || false
                            }
                        };
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
