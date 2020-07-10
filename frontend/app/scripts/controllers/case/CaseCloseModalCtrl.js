(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCloseModalCtrl',
        function($scope, $uibModalInstance, SearchSrv, CustomFieldsSrv, NotificationSrv, caze) {
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
                            "_id": $scope.caze._id
                        },
                        "_type": "case"
                    }
                }, {
                    '_in': {
                        '_field': 'status',
                        '_values': ['Waiting', 'InProgress']
                    }
                }]
            }, 'case_task', 'all');

            var getTemplateCustomFields = function(customFields) {
                var result = [];

                result = _.pluck(_.sortBy(_.map(customFields, function(definition, name){
                    return {
                        name: name,
                        order: definition.order
                    };
                }), 'order'), 'name');

                return result;
            };

            $scope.initialize = function() {
                CustomFieldsSrv.all().then(function(fields) {
                    $scope.orderedFields = getTemplateCustomFields($scope.caze.customFields);
                    $scope.allCustomFields = fields;

                    $scope.mandatoryFields = _.without(_.map($scope.orderedFields, function(cf) {
                        var fieldDef = fields[cf];

                        if(!fieldDef) {
                            return;
                        }

                        var fieldValue = $scope.caze.customFields[cf][cf.type];

                        if((fieldValue === undefined || fieldValue === null) && fieldDef.mandatory === true) {
                            return cf;
                        }
                    }), undefined);

                });

                $scope.formData = {
                    status: 'Resolved',
                    resolutionStatus: $scope.caze.resolutionStatus || 'Indeterminate',
                    summary: $scope.caze.summary || '',
                    impactStatus: $scope.caze.impactStatus || null
                };


                $scope.$watchCollection('formData', function(data, oldData) {
                    if (data.resolutionStatus !== oldData.resolutionStatus) {
                        data.impactStatus = null;
                    }
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
