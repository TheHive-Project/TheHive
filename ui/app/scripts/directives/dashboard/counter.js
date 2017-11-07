(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardCounter', function($http, $state, DashboardSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                options: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                metadata: '='
            },
            templateUrl: 'views/directives/dashboard/counter/view.html',
            link: function(scope) {
                scope.data = null;

                scope.load = function() {
                    if(!scope.options.entity) {
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var statsPromise = $http.post('./api/' + scope.options.entity.replace('_', '/') + '/_stats', {
                        query: query,
                        stats: _.map(scope.options.series || [], function(serie) {
                            var s = {_agg: serie.agg};

                            if(serie.agg !== 'count') {
                                s._field = serie.field;
                            }

                            return s;
                        })
                    });

                    statsPromise.then(function(response) {
                        var data = response.data;

                        scope.data = _.map(scope.options.series || [], function(serie) {
                            var name = serie.agg === 'count' ? 'count' : serie.agg + '_' + serie.field;
                            return {
                                name: name,
                                label: serie.label,
                                value: data[name]
                            }
                        });


                        // var values = _.pluck(_.values(response.data), scope.options.field);
                        //
                        // _.each(scope.options.series, function(serie) {
                        //     var key = serie.field,
                        //         agg = serie.agg,
                        //         dataKey = agg === 'count' ? 'count' : (agg + '_' + key),
                        //         columnKey = key + '.' + agg;
                        //
                        //     columns.push([columnKey].concat(_.pluck(values, dataKey)));
                        //
                        //     scope.types[columnKey] = serie.type || 'line';
                        //     scope.names[columnKey] = agg === 'count' ? 'count' : (agg + ' of ' + key);
                        //     scope.axes[columnKey] = (scope.types[columnKey] === 'bar') ? 'y2' : 'y';
                        // });



                    }, function(err) {
                        NotificationSrv.error('dashboardLine', err.data, err.status);
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
