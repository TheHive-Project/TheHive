(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dashboardItem', function(DashboardSrv, UserSrv, $uibModal, $timeout) {
        return {
            restrict: 'E',
            replace: true,
            scope: {
                rowIndex: '=',
                colIndex: '=',
                component: '=',
                metadata: '=',
                filter: '=?',
                autoload: '=',
                refreshOn: '@',
                resizeOn: '@',
                mode: '=',
                showEdit: '=',
                showRemove: '=',
                onRemove: '&'
            },
            templateUrl: 'views/directives/dashboard/item.html',
            link: function(scope/*, element*/) {
                scope.typeClasses = DashboardSrv.typeClasses;
                scope.timeIntervals = DashboardSrv.timeIntervals;
                scope.aggregations = DashboardSrv.aggregations;
                scope.serieTypes = DashboardSrv.serieTypes;
                scope.sortOptions = DashboardSrv.sortOptions;
                scope.skipFields = DashboardSrv.skipFields;
                scope.pickFields = DashboardSrv.pickFields;
                scope.fieldsForAggregation = DashboardSrv.fieldsForAggregation;

                scope.layout = {
                    activeTab: 0
                };
                scope.query = null;

                if(scope.component.id) {
                    scope.$on('edit-chart-' + scope.component.id, function(/*data*/) {
                        scope.editItem();
                    });
                }

                scope.editItem = function() {
                    var modalInstance = $uibModal.open({
                        scope: scope,
                        controller: ['$scope', '$uibModalInstance', function($scope, $uibModalInstance) {
                            $scope.cancel = function() {
                                $uibModalInstance.dismiss();
                            };

                            $scope.save = function() {
                                $uibModalInstance.close($scope.component.options);
                            };
                        }],
                        templateUrl: 'views/directives/dashboard/edit.dialog.html',
                        size: 'lg'
                    });

                    modalInstance.result.then(function(definition) {
                        var entity = scope.component.options.entity;

                        //if(!entity) {
                        if(!DashboardSrv.hasMinimalConfiguration(scope.component)) {
                            return;
                        }

                        // Set the computed query
                        definition.query = DashboardSrv.buildFiltersQuery(entity ? scope.metadata[entity].attributes : null, scope.component.options.filters);

                        // Set the computed querie of series if available
                        _.each(definition.series, function(serie) {
                            if(serie.filters) {
                                serie.query = DashboardSrv.buildFiltersQuery(scope.metadata[entity || serie.entity].attributes, serie.filters);
                            }
                        });

                        scope.component.options = definition;

                        $timeout(function() {
                            scope.$broadcast(scope.refreshOn, scope.filter);
                        }, 500);
                    });
                };

                scope.addFilter = function() {
                    scope.component.options.filters = scope.component.options.filters || [];

                    scope.component.options.filters.push({
                        field: null,
                        type: null
                    });
                };

                scope.removeFilter = function(index) {
                    scope.component.options.filters.splice(index, 1);
                };

                scope.setFilterField = function(filter, entity) {
                    var field = scope.metadata[entity].attributes[filter.field];

                    if(!field) {
                        return;
                    }

                    filter.type = field.type;

                    if (field.type === 'date') {
                        filter.value = {
                            from: null,
                            to: null
                        };
                    } else {
                        filter.value = null;
                    }
                };

                scope.addSerie = function() {
                    scope.component.options.series = scope.component.options.series || [];

                    scope.component.options.series.push({
                        agg: null,
                        field: null
                    });
                };

                scope.addSerieFilter = function(serie) {
                    serie.filters = serie.filters || [];

                    serie.filters.push({
                        field: null,
                        type: null
                    });
                };

                scope.removeSerieFilter = function(serie, index) {
                    serie.filters.splice(index, 1);
                };


                scope.removeSerie = function(index) {
                    scope.component.options.series.splice(index, 1);
                };

                scope.showQuery = function() {
                    scope.query = DashboardSrv.buildFiltersQuery(scope.metadata[scope.component.options.entity], scope.component.options.filters);
                };

                scope.clearProperty = function(obj, property) {
                    if(obj[property] !== undefined && obj[property] !== null) {
                      if(_.isArray(obj[property])){
                        obj[property] = [];
                      } else if (_.isObject(obj[property])) {
                        obj[property] = {};
                      } else {
                        obj[property] = null;
                      }
                    }
                };
            }
        };
    });
})();
