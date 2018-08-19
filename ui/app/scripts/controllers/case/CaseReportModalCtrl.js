(function () {
    'use strict';

    angular.module('theHiveControllers')
        .controller('CaseReportModalCtrl', CaseReportModalCtrl);

    function CaseReportModalCtrl($scope, $state, $uibModalInstance, $q, SearchSrv, CaseSrv, UserInfoSrv, NotificationSrv, CaseReportingTemplateSrv, PSearchSrv, caze, $http) {
        var self = this;

        this.caze = caze;
        this.templates = [];
        this.templatesLoaded = false;
        this.artifacts = [];
        this.artifactsLoaded = false;
        this.tasks = [];
        this.tasksLoaded = false;
        this.abstract = '';

		$scope.caseId = this.caze.id;
        
		$scope.artifactsList = PSearchSrv($scope.caseId, 'case_artifact', {
			scope: $scope,
			baseFilter: {
				'_and': [{
					'_parent': {
						"_type": "case",
						"_query": {
							"_id": $scope.caseId
						}
					}
				},   {
					'status': 'Ok'
				}]
			},
			filter : { '_any': '*' },
			skipStream : true,
			loadAll: true,
			sort: '-startDate',
			pageSize: 10,
			onUpdate: function () {
				self.artifacts = $scope.artifactsList.allValues;
				self.artifactsLoaded = true;
			},
			nstats: true
		});
        
        $scope.taskList = PSearchSrv($scope.caseId, 'case_task', {
            scope: $scope,
            loadAll: true,
            baseFilter: {
                _and: [{
                    _parent: {
                        _type: 'case',
                        _query: {
                            '_id': $scope.caseId
                        }
                    }
                }, {
                    _not: {
                        'status': 'Cancel'
                    }
                }]
            },
            sort: ['-flag', '+order', '+startDate', '+title'],
            onUpdate: function() {
				self.tasks = $scope.taskList.allValues;
				self.tasksLoaded = true;
            }
        });
        
        
        this.load = function(){
            $q.all([
                CaseReportingTemplateSrv.list()
            ]).then(function (response) {
                self.templates = response[0];
                self.templatesLoaded = true;
                return $q.resolve(self.templates);
            }, function(rejection){
                NotificationSrv.error('CaseReportModalCtrl', rejection.data, rejection.status);
            })
        };
        
        this.cancel = function () {
            $uibModalInstance.dismiss();
        };
        
        this.report = function(event) {
			var report = angular.element(document.querySelector('#casereport'));
			
			var printWindow = window.open('', '', 'height=400,width=800');
			printWindow.document.write(report.html());
			printWindow.document.close();
			printWindow.print();

			$uibModalInstance.close();
		};
		
		this.load();
    }
})();
