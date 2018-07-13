(function () {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCaseReportCtrl',
        function AdminCaseReportCtrl($scope, $q, CaseReportSrv, NotificationSrv){
            var self = this;
            self.templates = []
            self.formData = {}
            this.editorOptions = {
              useWrapMode: true,
              showGutter: true
            };


            this.load = function() {
              $q.all([
                  CaseReportSrv.list(),
              ]).then(function (response) {
                  self.templates = response[0];
                  console.log(self.templates[0])
                  self.formData.content = self.templates[0].content
                  self.formData.template_id = self.templates[0].id
                  return $q.resolve(self.template);
              }, function(rejection) {
                  NotificationSrv.error('CaseReport', rejection.data, rejection.status);
              })
            };
            this.load();
        });
})();
