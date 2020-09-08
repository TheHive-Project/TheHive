(function() {
    'use strict';
    angular.module('theHiveControllers').controller('CaseCloseModalCtrl',
        function($scope, $uibModalInstance, SearchSrv, CustomFieldsSrv, NotificationSrv, caze) {
            $scope.caze = caze;
            $scope.tasksValid = false;
            $scope.tasks = [];
            $scope.formData = {};
            $scope.customFieldsSrv = CustomFieldsSrv;

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

            $scope.initialize = function() {
                CustomFieldsSrv.all().then(function() {
                    $scope.mandatoryFields = _.sortBy(_.filter($scope.caze.customFields, function(cf) {
                        var fieldDef = $scope.customFieldsSrv.getCache(cf.name);

                        if(!fieldDef) {
                            return;
                        }

                        return ((cf.value === undefined || cf.value === null) && fieldDef.mandatory === true);
                    }), 'order');

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

            $scope.getCustomFieldsForUpdate = function() {
                var customFields = {};

                _.each($scope.caze.customFields, function(cf) {
                    customFields[cf.name] = {
                        order: cf.order
                    };

                    customFields[cf.name][cf.type] = cf.type === 'date' ? moment(cf.value).valueOf() : cf.value;
                });

                return customFields;
            };

            $scope.closeCase = function() {
                var data = $scope.formData;

                if (data.impactStatus === null) {
                    data.impactStatus = 'NotApplicable';
                }

                data.customFields = $scope.getCustomFieldsForUpdate();

                var promise = $scope.updateField(data);

                promise.then(function(caze) {
                    $scope.caze = caze;

                    NotificationSrv.log('The case #' + caze.number + ' has been closed', 'success');

                    $uibModalInstance.close();
                });
            };

            $scope.cancel = function() {
                $uibModalInstance.dismiss();
            };
        }
    );
})();
