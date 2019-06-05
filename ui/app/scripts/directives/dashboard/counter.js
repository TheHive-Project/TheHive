(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardCounter', function($q, $http, $state, DashboardSrv, NotificationSrv, GlobalSearchSrv) {
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
                scope.error = false;
                scope.data = null;
                scope.globalQuery = null;

                scope.load = function() {
                    if(!scope.entity) {
                        scope.error = true;
                        return;
                    }

                    var query = DashboardSrv.buildChartQuery(scope.filter, scope.options.query);
                    scope.globalQuery = query;

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
                        scope.error = false;
                        var data = response.data;

                        scope.data = _.map(scope.options.series || [], function(serie, index) {
                            var name = 'agg_' + (index + 1);
                            return {
                                serie: serie,
                                agg: serie.agg,
                                name: name,
                                label: serie.label,
                                value: data[name] || 0
                            };
                        });

                    }, function(err) {
                        scope.error = true;
                        NotificationSrv.error('dashboardBar', 'Failed to fetch data, please edit the widget definition', err.status);
                    });
                };

                scope.openSearch = function(item) {
                    if(scope.mode === 'edit') {
                        return;
                    }

                    var filters = (scope.options.filters || []).concat(item.serie.filters || []);

                    $q.resolve(GlobalSearchSrv.saveSection(scope.options.entity, {
                        search: filters.length === 0 ? '*' : null,
                        filters: filters
                    })).then(function() {
                        $state.go('app.search');
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
