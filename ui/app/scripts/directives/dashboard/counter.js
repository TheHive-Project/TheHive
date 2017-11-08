(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardCounter', function($http, $state, DashboardSrv, NotificationSrv) {
        return {
            restrict: 'E',
            scope: {
                filter: '=?',
                options: '=',
                entity: '=',
                autoload: '=',
                mode: '=',
                refreshOn: '@',
                metadata: '='
            },
            templateUrl: 'views/directives/dashboard/counter/view.html',
            link: function(scope) {
                scope.data = null;

                scope.load = function() {
                    if(!scope.entity) {
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);

                    var statsPromise = $http.post('./api' + scope.entity.path + '/_stats', {
                        query: query,
                        stats: _.map(scope.options.series || [], function(serie, index) {
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
                    });

                    statsPromise.then(function(response) {
                        var data = response.data;

                        scope.data = _.map(scope.options.series || [], function(serie, index) {
                            var name = 'agg_' + (index + 1);
                            return {
                                name: name,
                                label: serie.label,
                                value: data[name] || 0
                            }
                        });

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
