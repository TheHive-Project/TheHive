(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCloseModalCtrl',
        function($scope, $uibModalInstance, SearchSrv, MetricsCacheSrv, CustomFieldsCacheSrv, NotificationSrv, caze) {
            $scope.caze = caze;
            $scope.tasksValid = false;
            $scope.tasks = [];
            $scope.formData = {};

            SearchSrv(function(data) {
                $scope.initialize();
                $scope.tasks = data;

                if (data && data.length === 0) {
                    $scope.tasksValid = true;
                }
            }, {
                '_and': [{
                    '_parent': {
                        "_query": {
                            "_id": $scope.caze.id
                        },
                        "_type": "case"
                    }
                }, {
                    '_in': {
                        '_field': 'status',
                        '_values': ['waiting', 'inProgress']
                    }
                }]
            }, 'case_task', 'all');

            var getTemplateCustomFields = function(customFields) {
                var result = [];

                result = _.pluck(_.sortBy(_.map(customFields, function(definition, name){
                    return {
                        name: name,
                        order: definition.order
                    }
                }), function(item){
                    return item.order;
                }), 'name');

                return result;
            }

            $scope.initialize = function() {
                CustomFieldsCacheSrv.all().then(function(fields) {
                    $scope.orderedFields = getTemplateCustomFields($scope.caze.customFields);
                    $scope.allCustomFields = fields;                    

                    $scope.mandatoryFields = _.without(_.map($scope.orderedFields, function(cf) {
                        var fieldDef = fields[cf];
                        var fieldValue = $scope.caze.customFields[cf][cf.type];

                        if((fieldValue === undefined || fieldValue === null) && fieldDef.mandatory === true) {
                            return cf;
                        }
                    }), undefined);

                });
                MetricsCacheSrv.all().then(function(metricsCache) {

                    $scope.formData = {
                        status: 'Resolved',
                        resolutionStatus: $scope.caze.resolutionStatus || 'Indeterminate',
                        summary: $scope.caze.summary || '',
                        impactStatus: $scope.caze.impactStatus || null
                    };

                    $scope.metricsCache = metricsCache;

                    $scope.$watchCollection('formData', function(data, oldData) {
                        if (data.resolutionStatus !== oldData.resolutionStatus) {
                            data.impactStatus = null;
                        }
                    });
                });
            };

            $scope.confirmTaskClose = function() {
                $scope.tasksValid = true;
            };

            $scope.closeCase = function() {
                var data = $scope.formData;

                if (data.impactStatus === null) {
                    data.impactStatus = 'NotApplicable';
                }

                data.metrics = $scope.caze.metrics;
                data.customFields = $scope.caze.customFields;

                _.each($scope.mandatoryFields, function(cf) {
                    var field = data.customFields[cf];
                    var fieldDef = $scope.allCustomFields[cf];

                    if(fieldDef.type === 'date') {
                        field[fieldDef.type] = field[fieldDef.type] ? moment(field[fieldDef.type]).valueOf() : field[fieldDef.type];
                    }
                });

                var promise = $scope.updateField(data);

                promise.then(function(caze) {
                    $scope.caze = caze;

                    NotificationSrv.log('The case #' + caze.caseId + ' has been closed', 'success');

                    $uibModalInstance.close();
                });
            };

            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }
    );
})();
