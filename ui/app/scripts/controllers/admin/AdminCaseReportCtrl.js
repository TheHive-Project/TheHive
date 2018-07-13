(function () {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCaseReportCtrl',
        function AdminCaseReportCtrl($scope, $q, CaseReportSrv, NotificationSrv){
            var self = this;
            self.templates = []
            self.formData = {}
            self.editorOptions = {
              useWrapMode: true,
              showGutter: true
            };

            this.load = function() {
              $q.all([
                  CaseReportSrv.list(),
              ]).then(function (response) {
                  self.templates = response[0];
                  self.formData.content = self.templates[0].content
                  self.formData.name = self.templates[0].name
                  self.formData.template_id = self.templates[0].id
                  return $q.resolve(self.templates);
              }, function(rejection) {
                  NotificationSrv.error('CaseReport', rejection.data, rejection.status);
              })
            };
            this.load();

            $scope.updateReport = function(){
              $q.all([
                CaseReportSrv.update(self.formData.template_id, {
                    'name': self.formData.name,
                    'content': self.formData.content
                })
              ]).then(function (response){
                var res = response[0]
                if (res.status === 200){
                  NotificationSrv.log('Template updated successfully', 'success');
                }
                else{
                  NotificationSrv.error('CaseReport', res.data, res.status)
                }
              }, function(rejection){
                NotificationSrv.error('CaseReport', rejection.data, rejection.status)
              })
            };
        });
})();
